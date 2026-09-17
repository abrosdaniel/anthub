package dev.abros.anthub.server;

import com.google.gson.*;
import com.mojang.brigadier.arguments.*;
import dev.abros.anthub.core.*;
import dev.abros.anthub.network.Protocol;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class ServerFeatures {
    private static final Map<UUID,JsonObject> clients=new HashMap<>();
    private static final Map<UUID,Long> joined=new HashMap<>();
    private static final Map<String,Long> requests=new HashMap<>();
    private static final ExecutorService STORAGE=new ThreadPoolExecutor(4,4,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r->{Thread t=new Thread(r,"AntHub storage");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static final ExecutorService CONTROL=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),r->{var thread=new Thread(r,"AntHub server changes");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    private static final ExecutorService READS=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),r->{var thread=new Thread(r,"AntHub menu reads");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    private static final java.util.concurrent.atomic.AtomicBoolean delivering=new java.util.concurrent.atomic.AtomicBoolean();
    static void storage(Runnable task){STORAGE.execute(task);}
    private static void auditAsync(String actor,String action,String detail){try{storage(()->{try{ServerExtras.audit(actor,action,detail);}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().error("Cannot persist AntHub audit",ex);}});}catch(RejectedExecutionException ex){com.mojang.logging.LogUtils.getLogger().error("AntHub audit queue is full");}}
    private static CommunityReports reports;
    private record Subscription(String section,String id,long expires){}
    private static final Map<UUID,Subscription> subscriptions=new HashMap<>();
    private static final Set<UUID> menuClients=new HashSet<>();
    private static CommunityStore community;
    private static JsonObject communityConfig;
    private static final Map<UUID,Integer> unread=new HashMap<>();
    private static final Map<UUID,Long> popupSequence=new HashMap<>();
    private static boolean maintenance;
    private static String maintenanceReason="",restartReason="";
    private static long maintenanceUntil,restartAt,lastPush;
    private static Path settings;
    private static String originalMotd;
    private static long lastPrune,lastStatePush;private static final java.util.concurrent.atomic.AtomicBoolean refreshing=new java.util.concurrent.atomic.AtomicBoolean();
    public static void install(){
        Protocol.featureRequest=ServerFeatures::request;
        NeoForge.EVENT_BUS.addListener(ServerFeatures::commands);
        NeoForge.EVENT_BUS.addListener(ServerFeatures::start);
        NeoForge.EVENT_BUS.addListener(ServerFeatures::login);
        NeoForge.EVENT_BUS.addListener(ServerFeatures::logout);
        NeoForge.EVENT_BUS.addListener(ServerFeatures::tick);
    }
    private static void start(net.neoforged.neoforge.event.server.ServerStartingEvent e){
        originalMotd=e.getServer().getMotd();lastPrune=0;lastStatePush=0;
        menuClients.clear();subscriptions.clear();clients.clear();joined.clear();requests.clear();restartAt=0;lastPush=0;maintenance=false;maintenanceUntil=0;
        var database=ServerDatabase.get();
        settings=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("anthub");
        try{
            Path config=settings.resolve("community-settings.json");if(!Files.exists(config))Json.write(config,CommunityStore.defaults());
            communityConfig=CommunityStore.validateConfig(Json.read(config));community=new CommunityStore(database,communityConfig);unread.clear();popupSequence.clear();
            reports=new CommunityReports(community);ServerExtras.start(settings,community);
            var j=community.record("state","maintenance");var restart=community.record("state","restart");if(restart!=null){restartAt=restart.get("at").getAsLong();restartReason=Json.opt(restart,"reason","");}if(j!=null){maintenance=j.get("maintenance").getAsBoolean();maintenanceReason=Json.opt(j,"reason","");maintenanceUntil=j.get("until").getAsLong();}
        }
        catch(Exception ex){throw new IllegalStateException("Cannot initialize AntHub server features",ex);}
    }
    public static boolean admin(ServerPlayer p){return p.hasPermissions(2)||ServerIntegration.luckPermsEnabled()&&LuckPermsAdapter.profile(p.getUUID()).getAsJsonObject("capabilities").get("anthub.admin").getAsBoolean();}
    public static boolean mayJoin(com.mojang.authlib.GameProfile profile,MinecraftServer server){
        return !maintenance||server.getPlayerList().isOp(profile)||(ServerIntegration.luckPermsEnabled()&&LuckPermsAdapter.profile(profile.getId()).getAsJsonObject("capabilities").get("anthub.admin").getAsBoolean());
    }
    public static Component maintenanceMessage(){return Component.literal("Server maintenance: "+maintenanceReason+(maintenanceUntil>0?" · until "+java.time.Instant.ofEpochMilli(maintenanceUntil):""));}
    public static void record(UUID id,JsonObject state){var safe=new JsonObject();for(String k:List.of("coreVersion","packVersion","repository","lockSha256","requiredFilesDigest")){String value=Json.opt(state,k,"");safe.addProperty(k,value.substring(0,Math.min(256,value.length())));}clients.put(id,safe);}
    private static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){joined.put(p.getUUID(),System.currentTimeMillis());var actor=actor(p);String role=role(p);var store=community;try{STORAGE.execute(()->{try{store.seen(actor,role);}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot record player",ex);}});}catch(RejectedExecutionException busy){com.mojang.logging.LogUtils.getLogger().warn("AntHub: player activity queue is full");}sendState(p,false);invalidate(p.server);}}
    private static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){UUID id=e.getEntity().getUUID();menuClients.remove(id);subscriptions.remove(id);clients.remove(id);joined.remove(id);unread.remove(id);popupSequence.remove(id);requests.keySet().removeIf(key->key.startsWith(id.toString()+":"));if(e.getEntity() instanceof ServerPlayer p)p.server.execute(()->invalidate(p.server));}
    private static void send(ServerPlayer p,JsonObject j){
        if(!p.connection.hasChannel(Protocol.FeatureState.TYPE)||!ServerIntegration.supports(p,"menu"))return;
        String encoded=Json.GSON.toJson(j);if(encoded.length()>32767){encoded=Json.GSON.toJson(message("result","Response too large; read reports on the server"));}
        PacketDistributor.sendToPlayer(p,new Protocol.FeatureState(encoded));
    }
    private static JsonObject message(String kind,String text){var j=new JsonObject();j.addProperty("kind",kind);j.addProperty("text",text);return j;}
    private static void notice(MinecraftServer server,String text){for(ServerPlayer p:server.getPlayerList().getPlayers()){if(p.connection.hasChannel(Protocol.FeatureState.TYPE))send(p,noticeMessage(text, false));else p.sendSystemMessage(Component.literal(text));}}
    private static JsonObject noticeMessage(String text,boolean optional){var j=message("notice",text);j.addProperty("optional",optional);return j;}
    private static void announcement(MinecraftServer server,String text){for(ServerPlayer p:server.getPlayerList().getPlayers()){if(p.connection.hasChannel(Protocol.FeatureState.TYPE))send(p,noticeMessage(text,true));else p.sendSystemMessage(Component.literal(text));}}
    private static void invalidate(MinecraftServer server){invalidate(server,"","");}
    private static void invalidate(MinecraftServer server,String section,String id){
        long now=System.currentTimeMillis();subscriptions.values().removeIf(v->v.expires<now);
        for(var player:server.getPlayerList().getPlayers()){var sub=subscriptions.get(player.getUUID());if(sub==null)continue;
            if(!section.isEmpty()&&!sub.section.equals(section)&&!sub.section.equals("home")&&!sub.section.equals("notifications"))continue;
            if(!id.isEmpty()&&!sub.id.isEmpty()&&!sub.id.equals(id))continue;
            var hint=message("changed","");hint.addProperty("section",section);hint.addProperty("id",id);send(player,hint);
        }
    }
    private static void deliverChanges(MinecraftServer server){
        var store=community;if(store==null||!delivering.compareAndSet(false,true))return;
        try{CONTROL.execute(()->{try{var events=store.pendingChanges();server.execute(()->{
            if(community!=store){delivering.set(false);return;}
            try{
                long now=System.currentTimeMillis();subscriptions.values().removeIf(sub->sub.expires<now);
                for(var player:server.getPlayerList().getPlayers()){
                    var sub=subscriptions.get(player.getUUID());if(sub==null||!AuthServer.authenticated(player))continue;
                    boolean changed=false;
                    for(var element:events){var event=element.getAsJsonObject();String recipient=Json.str(event,"recipient"),topic=Json.str(event,"topic"),id=Json.str(event,"entity");
                        if(!recipient.isEmpty()&&!recipient.equals(player.getUUID().toString()))continue;
                        if(!sub.section.equals(topic)&&!sub.section.equals("home"))continue;
                        if(!sub.id.isEmpty()&&!sub.id.equals(id))continue;changed=true;break;
                    }
                    if(changed)send(player,message("changed",""));
                }
                CONTROL.execute(()->{try{store.acknowledgeChanges(events);}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot acknowledge AntHub changes; delivery will retry",ex);}finally{delivering.set(false);}});
            }catch(Exception ex){delivering.set(false);com.mojang.logging.LogUtils.getLogger().warn("Cannot deliver AntHub changes; delivery will retry",ex);}
        });}catch(Exception ex){delivering.set(false);com.mojang.logging.LogUtils.getLogger().warn("Cannot read AntHub changes; delivery will retry",ex);}});}catch(RejectedExecutionException busy){delivering.set(false);}
    }
    private static void sendState(ServerPlayer p,boolean open){
        if(!p.connection.hasChannel(Protocol.FeatureState.TYPE)||!ServerIntegration.supports(p,"menu"))return;
        var j=message("state","");j.addProperty("authReset",AuthServer.mayReset(p));j.add("actions",ServerExtras.actions(p));j.addProperty("requiredHash",ServerIntegration.requiredHash());j.addProperty("open",open);var profile=ServerIntegration.luckPermsEnabled()?LuckPermsAdapter.profile(p.getUUID()):null;j.addProperty("admin",p.hasPermissions(2)||profile!=null&&profile.getAsJsonObject("capabilities").get("anthub.admin").getAsBoolean());j.addProperty("name",p.getGameProfile().getName());j.addProperty("uuid",p.getUUID().toString());j.addProperty("sessionSeconds",Math.max(0,(System.currentTimeMillis()-joined.getOrDefault(p.getUUID(),System.currentTimeMillis()))/1000));
        j.addProperty("help",ServerIntegration.helpText());j.addProperty("description",originalMotd);j.addProperty("online",p.server.getPlayerCount());j.addProperty("maximum",p.server.getMaxPlayers());j.addProperty("repository",ServerIntegration.project());j.addProperty("packVersion",ServerIntegration.packVersion());
        j.addProperty("maintenance",maintenance);j.addProperty("maintenanceReason",maintenanceReason);j.addProperty("maintenanceUntil",maintenanceUntil);j.addProperty("restartAt",restartAt);j.addProperty("restartReason",restartReason);
        j.addProperty("menuProtocol",MenuProtocol.VERSION);j.addProperty("serverAntHubVersion",dev.abros.anthub.AntHub.VERSION);j.addProperty("unread",unread.getOrDefault(p.getUUID(),0));j.addProperty("popupSequence",popupSequence.getOrDefault(p.getUUID(),0L));var visible=communityConfig.deepCopy();var sections=new JsonArray();for(var section:visible.getAsJsonArray("sections"))if(ServerIntegration.supports(p,section.getAsString()))sections.add(section);visible.add("sections",sections);j.add("communityConfig",visible);var supported=new JsonArray();for(String feature:ConnectionCompatibility.FEATURES)if(ServerIntegration.supports(p,feature))supported.add(feature);j.add("features",supported);if(profile!=null)j.add("profile",profile);send(p,j);
    }
    private static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
        long now=System.currentTimeMillis();if(now-lastPush<1000)return;lastPush=now;deliverChanges(e.getServer());
        if(restartAt>0&&now>=restartAt){notice(e.getServer(),"Scheduled restart time reached: "+restartReason);restartAt=0;var store=community;try{STORAGE.submit(()->{try{store.deleteRecord("state","restart");}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot clear restart notice",ex);}});}catch(RejectedExecutionException ignored){}}
        if(maintenance)e.getServer().setMotd("[Maintenance] "+maintenanceReason);
        if(now-lastPrune>=3600000){var store=reports;try{STORAGE.submit(()->{try{store.prune(System.currentTimeMillis());community.purgeDeleted(System.currentTimeMillis());}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("AntHub report cleanup failed",ex);}});lastPrune=now;}catch(RejectedExecutionException busy){lastPrune=now-3600000+60000;}}
        if(now-lastStatePush>=5000){lastStatePush=now;for(ServerPlayer p:e.getServer().getPlayerList().getPlayers())sendState(p,false);var store=community;var ids=List.copyOf(joined.keySet());if(!refreshing.compareAndSet(false,true))return;try{STORAGE.submit(()->{try{store.reminders(now);var counts=new HashMap<UUID,Integer>();var sequences=new HashMap<UUID,Long>();for(var entry:store.notificationSummary(ids).entrySet()){counts.put(entry.getKey(),entry.getValue().unread());sequences.put(entry.getKey(),entry.getValue().sequence());}e.getServer().execute(()->{if(community==store){unread.putAll(counts);popupSequence.putAll(sequences);}});}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Community reminders failed",ex);}finally{refreshing.set(false);}});}catch(RejectedExecutionException ignored){refreshing.set(false);}}
    }
    private static void maintenance(MinecraftServer server,boolean enabled,int minutes,String reason)throws Exception{
        if(reason.length()>500)throw new IllegalArgumentException("Reason too long");
        String nextReason=enabled?reason:"";long nextUntil=enabled&&minutes>0?System.currentTimeMillis()+minutes*60000L:0;
        var data=Json.GSON.toJsonTree(Map.of("maintenance",enabled,"reason",nextReason,"until",nextUntil)).getAsJsonObject();
        persistState(server,"maintenance",data,()->{if(enabled&&!maintenance)originalMotd=server.getMotd();maintenance=enabled;maintenanceReason=nextReason;maintenanceUntil=nextUntil;server.setMotd(enabled?"[Maintenance] "+maintenanceReason:originalMotd);notice(server,enabled?maintenanceMessage().getString():"Maintenance ended");});
    }
    private static void restart(MinecraftServer server,int seconds,String reason)throws Exception{
        if(reason.length()>500)throw new IllegalArgumentException("Reason too long");long at=seconds==0?0:System.currentTimeMillis()+seconds*1000L;
        persistState(server,"restart",Json.GSON.toJsonTree(Map.of("at",at,"reason",reason)).getAsJsonObject(),()->{restartAt=at;restartReason=reason;notice(server,seconds==0?"Restart notification cancelled":"Restart in "+seconds+" seconds: "+reason);});
    }
    private static void persistState(MinecraftServer server,String key,JsonObject data,Runnable apply){var store=community;CONTROL.execute(()->{try{store.record("state",key,data);server.execute(()->{if(community==store)apply.run();});}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().error("Cannot persist AntHub server state",ex);server.execute(()->{for(var p:server.getPlayerList().getPlayers())if(admin(p))send(p,message("result","Server storage is unavailable; change was not applied"));});}});}
    private static JsonObject diagnostics(MinecraftServer server){var j=message("diagnostics","");var rows=new JsonArray();for(ServerPlayer p:server.getPlayerList().getPlayers()){
        if(rows.size()>=30)break;var row=clients.getOrDefault(p.getUUID(),new JsonObject()).deepCopy();row.addProperty("player",p.getGameProfile().getName());row.addProperty("matching",ServerIntegration.requiredHash().equals(Json.opt(row,"lockSha256","")));rows.add(row);
    }j.add("players",rows);j.addProperty("total",server.getPlayerCount());return j;}
    private static void request(JsonObject j,net.neoforged.neoforge.network.handling.IPayloadContext context){
        if(!(context.player() instanceof ServerPlayer p)||!AuthServer.authenticated(p)||!ServerIntegration.supports(p,"menu"))return;

        try{
            String action=Json.str(j,"action");
            if(!Set.of("state","report","reports","diagnostics","announce","maintenance","restart","menuData","myReports","reply","players","moderate","history","reloadMenu","community","myReport","subscribe").contains(action))throw new IllegalArgumentException("Unknown action");
            String rateKey=p.getUUID()+":"+action;long now=System.currentTimeMillis();if(now-requests.getOrDefault(rateKey,0L)<500){sendError(p,j,"Подождите немного перед повторным запросом");return;}requests.put(rateKey,now);
            if(action.equals("subscribe")){String section=Json.opt(j,"section","");if(section.isEmpty()){subscriptions.remove(p.getUUID());return;}if(!Set.of("home","board","groups","events","polls","ideas","notifications","players","reports","myReports","history","links").contains(section))throw new IllegalArgumentException("Unknown section");String id=Json.opt(j,"id","");if(id.length()>36)throw new IllegalArgumentException("Invalid id");var previous=subscriptions.put(p.getUUID(),new Subscription(section,id,now+30000));if(previous==null||!previous.section.equals(section)||!previous.id.equals(id))send(p,message("changed",""));return;}
            if(action.equals("state")){if(!j.has("menuProtocol")||j.get("menuProtocol").getAsInt()!=MenuProtocol.VERSION){int clientProtocol=j.has("menuProtocol")?j.get("menuProtocol").getAsInt():0;
                com.mojang.logging.LogUtils.getLogger().warn("AntHub: несовместимый протокол меню игрока {}: клиент {}, сервер {}. Проверьте обновления AntHub на сервере и совместимость клиента.",p.getGameProfile().getName(),clientProtocol,MenuProtocol.VERSION);
                send(p,message("incompatible",clientProtocol<MenuProtocol.VERSION?"Обновите AntHub, чтобы открыть меню этого сервера.":"Эта версия AntHub пока несовместима с меню сервера. Попробуйте подключиться позже."));return;}menuClients.add(p.getUUID());if(j.has("client"))record(p.getUUID(),j.getAsJsonObject("client"));sendState(p,false);return;}
            if(!menuClients.contains(p.getUUID())){send(p,message("incompatible","Сначала требуется согласовать версию AntHub."));return;}
            if(action.equals("community")&&!ServerIntegration.supports(p,Json.opt(j,"section",""))){sendError(p,j,"Раздел недоступен в этой версии AntHub");return;}
            if(action.equals("players")&&!ServerIntegration.supports(p,"players"))return;
            if(action.equals("community")){
                String operation=Json.str(j,"op");
                if(j.has("location")&&!j.get("location").isJsonNull()){var location=CommunityLocation.read(j.getAsJsonObject("location"));var dimension=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(location.dimension()));if(p.server.getLevel(dimension)==null)throw new IllegalArgumentException("Такого измерения нет на этом сервере");}if(!Set.of("list","detail").contains(operation)){UUID.fromString(Json.str(j,"operationId"));if(!j.has("issuedAt"))throw new IllegalArgumentException("Missing command timestamp");}
                var actor=actor(p);var copy=j.deepCopy();var store=community;
                (Set.of("list","detail").contains(operation)?READS:STORAGE).submit(()->{try{var currentActor=p.server.submit(()->{
                    if(!p.connection.getConnection().isConnected()||!AuthServer.authenticated(p))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Сеанс завершён");
                    return actor(p);
                }).get(2,TimeUnit.SECONDS);
                var result=store.request(currentActor,copy);p.server.execute(()->{if(community==store&&p.connection.hasChannel(Protocol.FeatureState.TYPE)){if(!currentActor.equals(actor(p))){sendFailure(p,copy,new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Права изменились. Обновите раздел."));return;}send(p,result);if(!Set.of("list","detail").contains(Json.opt(copy,"op","")))invalidate(p.server,Json.opt(copy,"section",""),Json.opt(copy,"id",""));}});}
                catch(Exception ex){p.server.execute(()->{sendFailure(p,copy,ex);});}});return;
            }
            if(action.equals("players")){
                int page=bounded(j,"page",1000);String query=Json.opt(j,"query","");if(query.length()>32)throw new IllegalArgumentException("Search too long");
                boolean onlineOnly=!j.has("all")||!j.get("all").getAsBoolean();var online=new HashMap<String,String>();var roles=new HashMap<String,String>();var metadata=new HashMap<String,JsonObject>();for(var player:p.server.getPlayerList().getPlayers()){online.put(player.getUUID().toString(),player.getGameProfile().getName());roles.put(player.getUUID().toString(),role(player));if(ServerIntegration.luckPermsEnabled())metadata.put(player.getUUID().toString(),LuckPermsAdapter.profile(player.getUUID()));}var actions=ServerExtras.actions(p);var store=community;
                READS.submit(()->{try{var data=message("players","");data.addProperty("page",page);data.addProperty("request",Json.opt(j,"request",""));var rows=new JsonArray();if(onlineOnly){var onlineRows=new JsonArray();String scope="online:"+query,after=PlayerCursor.after(Json.opt(j,"cursor",""),scope);online.entrySet().stream().filter(x->x.getKey().compareTo(after)>0&&x.getValue().toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))).sorted(Map.Entry.comparingByKey()).limit(21).forEach(x->{var row=new JsonObject();row.addProperty("uuid",x.getKey());row.addProperty("name",x.getValue());row.addProperty("online",true);onlineRows.add(row);});var batch=PlayerCursor.page(onlineRows,scope);rows=batch.getAsJsonArray("entries");data.add("nextCursor",batch.get("nextCursor"));}else{var batch=store.peopleCursor(query,Json.opt(j,"cursor",""));rows=batch.getAsJsonArray("entries");data.add("nextCursor",batch.get("nextCursor"));for(var row:rows)row.getAsJsonObject().addProperty("online",online.containsKey(Json.str(row.getAsJsonObject(),"uuid")));}for(var element:rows){var row=element.getAsJsonObject();var meta=metadata.get(Json.str(row,"uuid"));if(meta!=null){row.addProperty("prefix",Json.opt(meta,"prefix",""));row.addProperty("suffix",Json.opt(meta,"suffix",""));}}data.add("players",rows);data.add("actions",actions);p.server.execute(()->send(p,data));}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});return;
            }
            if(action.equals("moderate")){UUID.fromString(Json.str(j,"operationId"));ServerExtras.moderate(p,j);invalidate(p.server);return;}
            if(action.equals("report")){UUID.fromString(Json.str(j,"operationId"));var store=reports;var copy=j.deepCopy();STORAGE.submit(()->{try{var receipt=store.submitRequest(p.getUUID(),p.getGameProfile().getName(),copy,System.currentTimeMillis());p.server.execute(()->{receipt.addProperty("kind","result");receipt.addProperty("request",Json.opt(copy,"request",""));send(p,receipt);invalidate(p.server);});}catch(Exception ex){p.server.execute(()->sendFailure(p,j,ex));}});return;}
            if(action.equals("menuData")){var data=message("menuData","");data.addProperty("request",Json.opt(j,"request",""));data.add("menu",ServerExtras.menu());send(p,data);return;}
            if(action.equals("myReport")){String id=Json.str(j,"id");var store=reports;READS.submit(()->{try{var data=message("myReports","");data.addProperty("page",0);data.addProperty("request",Json.opt(j,"request",""));var entries=new JsonArray();entries.add(store.get(p.getUUID(),id));data.add("reports",entries);p.server.execute(()->send(p,data));}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});return;}
            if(action.equals("myReports")){int page=bounded(j,"page",1000);READS.submit(()->{try{var data=message("myReports","");data.addProperty("page",page);data.addProperty("request",Json.opt(j,"request",""));var batch=reports.cursor(p.getUUID(),Json.opt(j,"cursor",""));data.add("reports",batch.get("entries"));data.add("nextCursor",batch.get("nextCursor"));p.server.execute(()->send(p,data));}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});return;}
            if(!admin(p))throw new IllegalArgumentException("Administrator permission required");
            switch(action){
                case "reloadMenu" -> {ServerExtras.reload();var next=CommunityStore.validateConfig(Json.read(settings.resolve("community-settings.json")));community.configure(next);communityConfig=next;}
                case "history" -> {int page=bounded(j,"page",100);READS.submit(()->{try{var data=message("history","");data.addProperty("page",page);data.addProperty("request",Json.opt(j,"request",""));var batch=community.recordCursor("audit","",Json.opt(j,"cursor",""),10);data.add("entries",batch.get("entries"));data.add("nextCursor",batch.get("nextCursor"));p.server.execute(()->{if(admin(p))send(p,data);});}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});}
                case "reply" -> {UUID.fromString(Json.str(j,"operationId"));String id=Json.str(j,"id"),text=Json.str(j,"text");boolean resolved=j.get("resolved").getAsBoolean();var store=reports;String author=p.getGameProfile().getName();STORAGE.submit(()->{try{if(!p.server.submit(()->p.connection.getConnection().isConnected()&&AuthServer.authenticated(p)&&admin(p)).get(2,TimeUnit.SECONDS))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Недостаточно прав");var receipt=store.replyRequest(p.getUUID().toString(),author,j);p.server.execute(()->{receipt.addProperty("kind","result");receipt.addProperty("request",Json.opt(j,"request",""));send(p,receipt);invalidate(p.server);});}catch(Exception ex){p.server.execute(()->sendFailure(p,j,ex));}});}

                case "diagnostics" -> send(p,diagnostics(p.server));
                case "reports" -> {int page=j.get("page").getAsInt();var store=reports;STORAGE.submit(()->{try{var result=message("reports","");result.addProperty("page",page);result.addProperty("request",Json.opt(j,"request",""));var batch=store.cursor(null,Json.opt(j,"cursor",""));result.add("reports",batch.get("entries"));result.add("nextCursor",batch.get("nextCursor"));p.server.execute(()->{if(admin(p))send(p,result);});}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});}
                case "announce" -> {String text=Json.str(j,"text").strip();if(text.isEmpty()||text.length()>500)throw new IllegalArgumentException("Message must contain 1–500 characters");announcement(p.server,text);}
                case "maintenance" -> maintenance(p.server,j.get("enabled").getAsBoolean(),bounded(j,"minutes",1440),Json.str(j,"text"));
                case "restart" -> restart(p.server,bounded(j,"seconds",86400),Json.opt(j,"text",""));
                default -> throw new IllegalArgumentException("Unknown action");
            }
            if(Set.of("announce","maintenance","restart","reloadMenu").contains(action)){auditAsync(p.getGameProfile().getName(),action,Json.GSON.toJson(j));send(p,message("result",Set.of("maintenance","restart").contains(action)?"Change queued; confirmation follows after saving":"Action completed"));}
        }catch(RejectedExecutionException busy){sendError(p,j,"Server storage is busy; try again shortly");}catch(Exception ex){sendFailure(p,j,ex);}
    }
    private static void sendFailure(ServerPlayer p,JsonObject request,Exception ex){
        String code=ex instanceof CommunityFailure failure?failure.code().name():ex instanceof IllegalArgumentException?"INVALID":"UNAVAILABLE";
        String diagnostic=UUID.randomUUID().toString();
        if(code.equals("UNAVAILABLE"))com.mojang.logging.LogUtils.getLogger().error("AntHub request failed [{}] ({})",diagnostic,ex.getClass().getSimpleName());
        var result=message(Json.opt(request,"action","").equals("community")?"community":"result",safeError(ex));result.addProperty("request",Json.opt(request,"request",""));result.addProperty("error",true);result.addProperty("code",code);result.addProperty("recovery",code.equals("CONFLICT")?"refresh":code.equals("INVALID")?"edit":code.equals("FORBIDDEN")?"close":"retry");result.addProperty("diagnosticId",diagnostic);send(p,result);
    }
    private static String safeError(Exception ex){return ex instanceof IllegalArgumentException?ex.getMessage():"Хранилище временно недоступно. Повторите запрос позже.";}
    private static void sendError(ServerPlayer p,JsonObject request,String text){var result=message(Json.opt(request,"action","").equals("community")?"community":"result",text);result.addProperty("request",Json.opt(request,"request",""));result.addProperty("error",true);send(p,result);}
    private static String role(ServerPlayer p){return ServerIntegration.luckPermsEnabled()?Json.opt(LuckPermsAdapter.profile(p.getUUID()),"primaryGroup","Игрок"):admin(p)?"Администратор":"Игрок";}
    private static CommunityStore.Actor actor(ServerPlayer p){return new CommunityStore.Actor(p.getUUID().toString(),p.getGameProfile().getName(),admin(p),p.hasPermissions(2)||ServerIntegration.luckPermsEnabled()&&LuckPermsAdapter.profile(p.getUUID()).getAsJsonObject("capabilities").get("anthub.events").getAsBoolean());}
    private static int bounded(JsonObject j,String key,int max){int value=j.get(key).getAsInt();if(value<0||value>max)throw new IllegalArgumentException("Invalid "+key);return value;}
    private interface Work {void run(CommandSourceStack source)throws Exception;}
    private static int execute(CommandSourceStack source,String command,Work work){try{work.run(source);auditAsync(source.getTextName(),"/ah", command);return 1;}catch(Exception ex){source.sendFailure(Component.literal(safeError(ex)));return 0;}}
    private static boolean admin(CommandSourceStack source){return source.hasPermission(2)||source.getEntity() instanceof ServerPlayer p&&admin(p);}
    private static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("ah").executes(c->execute(c.getSource(),c.getInput(),s->{var p=s.getPlayerOrException();if(!p.connection.hasChannel(Protocol.FeatureState.TYPE))throw new IllegalArgumentException("Install AntHub to open the menu");sendState(p,true);}))
            .then(Commands.literal("status").requires(ServerFeatures::admin).executes(c->execute(c.getSource(),c.getInput(),s->s.sendSuccess(()->Component.literal(Json.GSON.toJson(diagnostics(s.getServer()))),false))))
            .then(Commands.literal("announce").requires(ServerFeatures::admin).then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->execute(c.getSource(),c.getInput(),s->{String text=StringArgumentType.getString(c,"text");if(text.length()>500)throw new IllegalArgumentException("Message too long");announcement(s.getServer(),text);}))))
            .then(Commands.literal("maintenance").requires(ServerFeatures::admin)
                .then(Commands.literal("off").executes(c->execute(c.getSource(),c.getInput(),s->maintenance(s.getServer(),false,0,""))))
                .then(Commands.literal("on").then(Commands.argument("minutes",IntegerArgumentType.integer(0,1440)).then(Commands.argument("reason",StringArgumentType.greedyString()).executes(c->execute(c.getSource(),c.getInput(),s->maintenance(s.getServer(),true,IntegerArgumentType.getInteger(c,"minutes"),StringArgumentType.getString(c,"reason"))))))))
            .then(Commands.literal("restart").requires(ServerFeatures::admin)
                .then(Commands.literal("cancel").executes(c->execute(c.getSource(),c.getInput(),s->restart(s.getServer(),0,""))))
                .then(Commands.argument("seconds",IntegerArgumentType.integer(1,86400)).then(Commands.argument("reason",StringArgumentType.greedyString()).executes(c->execute(c.getSource(),c.getInput(),s->restart(s.getServer(),IntegerArgumentType.getInteger(c,"seconds"),StringArgumentType.getString(c,"reason")))))))
            .then(Commands.literal("reports").requires(ServerFeatures::admin).executes(c->execute(c.getSource(),c.getInput(),s->{STORAGE.submit(()->{try{String result=Json.GSON.toJson(reports.list(0));s.getServer().execute(()->{if(admin(s))s.sendSuccess(()->Component.literal(result),false);});}catch(Exception ex){s.getServer().execute(()->s.sendFailure(Component.literal("Cannot read reports")));}});})))) ;
    }
}
