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
    private record Subscription(String section,String id,long expires,long resynced){}
    private static final FailureTracker failures=new FailureTracker();
    private static final Map<UUID,Subscription> subscriptions=new HashMap<>();
    private static final Set<UUID> menuClients=new HashSet<>();
    private static CommunityStore community;
    private static JsonObject communityConfig;
    private static final Map<UUID,Integer> unread=new HashMap<>();
    private static final Map<UUID,Long> popupSequence=new HashMap<>();
    private static boolean maintenance;
    private static String maintenanceReason="",restartReason="";
    private static long maintenanceUntil,restartAt,lastPush;
    private static boolean timerPending;
    private static long pinnedUntil;
    private static String pinnedText="";
    private static Path settings;
    private static String originalMotd;
    private static long lastPrune,lastStatePush;private static final java.util.concurrent.atomic.AtomicBoolean refreshing=new java.util.concurrent.atomic.AtomicBoolean();
    public static void install(){
        Protocol.featureRequest=ServerFeatures::request;
        ServerPlayerStatistics.install();ServerModerationVotes.install();
        NeoForge.EVENT_BUS.addListener(ServerFeatures::commands);
        NeoForge.EVENT_BUS.addListener(ServerFeatures::start);
        NeoForge.EVENT_BUS.addListener(ServerFeatures::login);
        NeoForge.EVENT_BUS.addListener(ServerFeatures::logout);
        NeoForge.EVENT_BUS.addListener(ServerFeatures::tick);
    }
    private static void start(net.neoforged.neoforge.event.server.ServerStartingEvent e){
        timerPending=false;failures.clear();originalMotd=e.getServer().getMotd();lastPrune=0;lastStatePush=0;
        menuClients.clear();subscriptions.clear();clients.clear();joined.clear();requests.clear();restartAt=0;lastPush=0;maintenance=false;maintenanceUntil=0;
        var database=ServerDatabase.get();
        settings=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("anthub");
        try{

            communityConfig=ServerDatabase.settings().community();community=new CommunityStore(database,communityConfig);unread.clear();popupSequence.clear();
            reports=new CommunityReports(community);ServerExtras.start(settings,community);var pin=new CommunityAdministration(database,community).pin();pinnedUntil=pin==null||Json.opt(pin,"text","").isBlank()?0:pin.get("until").getAsLong();pinnedText=pin==null?"":Json.opt(pin,"text","");
            var j=community.record("state","maintenance");community.deleteRecord("state","restart");if(j!=null){maintenance=j.get("maintenance").getAsBoolean();maintenanceReason=Json.opt(j,"reason","");maintenanceUntil=j.get("until").getAsLong();if(maintenanceUntil>0&&maintenanceUntil<=System.currentTimeMillis()){maintenance=false;maintenanceUntil=0;community.deleteRecord("state","maintenance");}}
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
    static void send(ServerPlayer p,JsonObject j){
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
            if(!section.isEmpty()&&!sub.section.equals(section)&&!(sub.section.equals("home")&&Set.of("home","groups","events","players").contains(section))&&!sub.section.equals("notifications"))continue;
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
                    boolean changed=false;String changedTopic="",changedId="";int changedCount=0;
                    for(var element:events){var event=element.getAsJsonObject();String recipient=Json.str(event,"recipient"),topic=Json.str(event,"topic"),id=Json.str(event,"entity");
                        if(!recipient.isEmpty()&&!recipient.equals(player.getUUID().toString()))continue;
                        if(!sub.section.equals(topic)&&!(sub.section.equals("home")&&Set.of("home","groups","events","players").contains(topic)))continue;
                        if(!sub.id.isEmpty()&&!sub.id.equals(id))continue;changed=true;changedTopic=topic;changedId=id;changedCount++;
                    }
                    if(changed){var hint=message("changed","");if(changedCount==1){hint.addProperty("section",changedTopic);hint.addProperty("id",changedId);}send(player,hint);}
                }
                CONTROL.execute(()->{try{store.acknowledgeChanges(events);}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot acknowledge AntHub changes; delivery will retry",ex);}finally{delivering.set(false);}});
            }catch(Exception ex){delivering.set(false);com.mojang.logging.LogUtils.getLogger().warn("Cannot deliver AntHub changes; delivery will retry",ex);}
        });}catch(Exception ex){delivering.set(false);com.mojang.logging.LogUtils.getLogger().warn("Cannot read AntHub changes; delivery will retry",ex);}});}catch(RejectedExecutionException busy){delivering.set(false);}
    }
    private static void sendState(ServerPlayer p,boolean open){
        if(!p.connection.hasChannel(Protocol.FeatureState.TYPE)||!ServerIntegration.supports(p,"menu"))return;
        var j=message("state","");j.addProperty("authReset",AuthServer.mayReset(p));j.add("actions",ServerExtras.actions(p));j.addProperty("requiredHash",ServerIntegration.requiredHash());j.addProperty("open",open);var profile=ServerIntegration.luckPermsEnabled()?LuckPermsAdapter.profile(p.getUUID()):null;j.addProperty("admin",p.hasPermissions(2)||profile!=null&&profile.getAsJsonObject("capabilities").get("anthub.admin").getAsBoolean());j.addProperty("name",p.getGameProfile().getName());j.addProperty("uuid",p.getUUID().toString());if(ServerPlayerStatistics.store()!=null&&ServerPlayerStatistics.store().settings().session())j.addProperty("sessionSeconds",Math.max(0,(System.currentTimeMillis()-joined.getOrDefault(p.getUUID(),System.currentTimeMillis()))/1000));
        j.addProperty("help",ServerIntegration.helpText());j.addProperty("description",originalMotd);j.addProperty("online",p.server.getPlayerCount());j.addProperty("maximum",p.server.getMaxPlayers());j.addProperty("repository",ServerIntegration.project());j.addProperty("packVersion",ServerIntegration.packVersion());
        var permissions=new JsonObject();boolean full=p.hasPermissions(2)||profile!=null&&profile.getAsJsonObject("capabilities").get("anthub.admin").getAsBoolean();for(String right:ServerCommands.RIGHTS){var caps=profile==null?null:profile.getAsJsonObject("capabilities");permissions.addProperty(right,full||caps!=null&&caps.has(right)&&caps.get(right).getAsBoolean());}j.addProperty("staff",full||ServerCommands.RIGHTS.stream().filter(k->!k.startsWith("anthub.stats.")).anyMatch(k->permissions.get(k).getAsBoolean()));j.add("capabilities",permissions);j.addProperty("pinned",pinnedUntil>System.currentTimeMillis());if(permissions.get("anthub.announce").getAsBoolean()){j.addProperty("pinnedText",pinnedUntil>System.currentTimeMillis()?pinnedText:"");j.addProperty("pinnedUntil",pinnedUntil);}
        j.addProperty("maintenance",maintenance);j.addProperty("maintenanceReason",maintenanceReason);j.addProperty("maintenanceUntil",maintenanceUntil);j.addProperty("restartAt",restartAt);j.addProperty("restartReason",restartReason);
        if(ServerIntegration.supports(p,"moderation-votes"))j.add("moderationVotes",ServerModerationVotes.configuration(p.server));j.addProperty("menuProtocol",MenuProtocol.VERSION);j.addProperty("serverAntHubVersion",dev.abros.anthub.AntHub.VERSION);j.addProperty("unread",unread.getOrDefault(p.getUUID(),0));j.addProperty("popupSequence",popupSequence.getOrDefault(p.getUUID(),0L));var visible=communityConfig.deepCopy();var sections=new JsonArray();for(var section:visible.getAsJsonArray("sections"))if(ServerIntegration.supports(p,section.getAsString()))sections.add(section);visible.add("sections",sections);j.add("communityConfig",visible);var supported=new JsonArray();for(String feature:ConnectionCompatibility.FEATURES)if(ServerIntegration.supports(p,feature))supported.add(feature);j.add("features",supported);if(profile!=null)j.add("profile",profile);send(p,j);
    }
    private static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
        long now=System.currentTimeMillis();if(now-lastPush<1000)return;lastPush=now;deliverChanges(e.getServer());
        if(!timerPending&&(restartAt>0&&now>=restartAt||maintenance&&maintenanceUntil>0&&now>=maintenanceUntil)){
            boolean stop=restartAt>0&&now>=restartAt;long expected=stop?restartAt:maintenanceUntil;timerPending=true;var store=community;
            try{CONTROL.execute(()->{try{boolean consumed=store.inTransaction(()->{var saved=store.record("state",stop?"restart":"maintenance");if(saved==null||saved.get(stop?"at":"until").getAsLong()!=expected)return false;store.deleteRecord("state",stop?"restart":"maintenance");store.audit("server",stop?"scheduled shutdown":"maintenance expired",Long.toString(expected));return true;});if(!consumed){e.getServer().execute(()->timerPending=false);return;}e.getServer().execute(()->{timerPending=false;if(community!=store)return;if(stop&&restartAt==expected){restartAt=0;notice(e.getServer(),"Сервер сохраняется и останавливается. Повторный запуск выполняет панель.");e.getServer().halt(false);}else if(!stop&&maintenanceUntil==expected){maintenance=false;maintenanceUntil=0;e.getServer().setMotd(originalMotd);notice(e.getServer(),"Техническое обслуживание завершено");}});}catch(Exception ex){e.getServer().execute(()->timerPending=false);com.mojang.logging.LogUtils.getLogger().warn("AntHub: cannot complete scheduled action; will retry");}});}catch(RejectedExecutionException busy){timerPending=false;}
        }
        if(maintenance)e.getServer().setMotd("[Maintenance] "+maintenanceReason);
        if(now-lastPrune>=3600000){var store=reports;try{STORAGE.submit(()->{try{store.prune(System.currentTimeMillis());community.purgeDeleted(System.currentTimeMillis());}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("AntHub report cleanup failed",ex);}});lastPrune=now;}catch(RejectedExecutionException busy){lastPrune=now-3600000+60000;}}
        if(now-lastStatePush>=5000){lastStatePush=now;for(ServerPlayer p:e.getServer().getPlayerList().getPlayers())sendState(p,false);var store=community;var ids=List.copyOf(joined.keySet());if(!refreshing.compareAndSet(false,true))return;try{STORAGE.submit(()->{try{store.reminders(now);var counts=new HashMap<UUID,Integer>();var sequences=new HashMap<UUID,Long>();for(var entry:store.notificationSummary(ids).entrySet()){counts.put(entry.getKey(),entry.getValue().unread());sequences.put(entry.getKey(),entry.getValue().sequence());}e.getServer().execute(()->{if(community==store){unread.putAll(counts);popupSequence.putAll(sequences);}});}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Community reminders failed",ex);}finally{refreshing.set(false);}});}catch(RejectedExecutionException ignored){refreshing.set(false);}}
    }
    private static void maintenance(MinecraftServer server,boolean enabled,int minutes,String reason,CommandSourceStack source)throws Exception{
        if(reason.length()>500)throw new IllegalArgumentException("Reason too long");
        String nextReason=enabled?reason:"";long nextUntil=enabled&&minutes>0?System.currentTimeMillis()+minutes*60000L:0;
        var data=Json.GSON.toJsonTree(Map.of("maintenance",enabled,"reason",nextReason,"until",nextUntil)).getAsJsonObject();
        persistState(server,"maintenance",data,source,()->{if(enabled&&!maintenance)originalMotd=server.getMotd();maintenance=enabled;maintenanceReason=nextReason;maintenanceUntil=nextUntil;server.setMotd(enabled?"[Maintenance] "+maintenanceReason:originalMotd);notice(server,enabled?maintenanceMessage().getString():"Maintenance ended");});
    }
    private static void restart(MinecraftServer server,int seconds,String reason,CommandSourceStack source)throws Exception{
        if(reason.length()>500)throw new IllegalArgumentException("Reason too long");long at=seconds==0?0:System.currentTimeMillis()+seconds*1000L;
        persistState(server,"restart",Json.GSON.toJsonTree(Map.of("at",at,"reason",reason)).getAsJsonObject(),source,()->{restartAt=at;restartReason=reason;notice(server,seconds==0?"Запланированная остановка отменена":"Остановка через "+seconds+" с: "+reason+". Повторный запуск обеспечивает панель.");});
    }
    private static void persistState(MinecraftServer server,String key,JsonObject data,CommandSourceStack source,Runnable apply){var store=community;CONTROL.execute(()->{try{
        if(!server.submit(()->ServerCommands.allowed(source,"anthub."+key)).get(2,TimeUnit.SECONDS))throw new IllegalArgumentException("Права изменились");
        store.inTransaction(()->{store.record("state",key,data);store.audit(source.getTextName(),key,Json.GSON.toJson(data));return null;});
        server.execute(()->{if(community==store){apply.run();ServerCommands.reply(source,"Сохранено");for(var player:server.getPlayerList().getPlayers())sendState(player,false);}});
    }catch(Exception ex){server.execute(()->ServerCommands.error(source,ex));}});}
    private static boolean allowedAction(ServerPlayer player,String action){return switch(action){
        case "announce","pinAnnouncement" -> ServerCommands.allowed(player,"anthub.announce");
        case "maintenance","restart" -> ServerCommands.allowed(player,"anthub."+action);
        case "reports","reply" -> ServerCommands.allowed(player,"anthub.reports");
        case "diagnostics" -> ServerCommands.allowed(player,"anthub.diagnostics");
        default -> admin(player);
    };}
    private static JsonObject diagnostics(MinecraftServer server){var j=message("diagnostics","");var rows=new JsonArray();for(ServerPlayer p:server.getPlayerList().getPlayers()){
        if(rows.size()>=30)break;var row=clients.getOrDefault(p.getUUID(),new JsonObject()).deepCopy();row.addProperty("player",p.getGameProfile().getName());row.addProperty("matching",ServerIntegration.requiredHash().equals(Json.opt(row,"lockSha256","")));rows.add(row);
    }j.add("players",rows);j.addProperty("total",server.getPlayerCount());return j;}
    private static void request(JsonObject j,net.neoforged.neoforge.network.handling.IPayloadContext context){
        if(!(context.player() instanceof ServerPlayer p)||!AuthServer.authenticated(p)||!ServerIntegration.supports(p,"menu"))return;

        try{
            String action=Json.str(j,"action");
            if(!Set.of("state","report","reports","diagnostics","announce","maintenance","restart","menuData","myReports","reply","players","moderate","history","reloadMenu","community","myReport","subscribe","pinAnnouncement","exportCommunity","playerAdministration","moderationVote").contains(action))throw new IllegalArgumentException("Unknown action");
            String rateKey=p.getUUID()+":"+action;long now=System.currentTimeMillis();if(now-requests.getOrDefault(rateKey,0L)<500){sendError(p,j,"Подождите немного перед повторным запросом");return;}requests.put(rateKey,now);
            if(action.equals("subscribe")){String section=Json.opt(j,"section","");if(section.isEmpty()){subscriptions.remove(p.getUUID());return;}if(!Set.of("home","board","groups","events","polls","ideas","notifications","players","reports","myReports","history","links").contains(section))throw new IllegalArgumentException("Unknown section");String id=Json.opt(j,"id","");if(id.length()>36)throw new IllegalArgumentException("Invalid id");var previous=subscriptions.get(p.getUUID());boolean resync=previous==null||!previous.section.equals(section)||!previous.id.equals(id)||now-previous.resynced>=30000;subscriptions.put(p.getUUID(),new Subscription(section,id,now+30000,resync?now:previous.resynced));if(resync)send(p,message("changed",""));return;}
            if(action.equals("state")){if(!j.has("menuProtocol")||j.get("menuProtocol").getAsInt()!=MenuProtocol.VERSION){int clientProtocol=j.has("menuProtocol")?j.get("menuProtocol").getAsInt():0;
                com.mojang.logging.LogUtils.getLogger().warn("AntHub: несовместимый протокол меню игрока {}: клиент {}, сервер {}. Проверьте обновления AntHub на сервере и совместимость клиента.",p.getGameProfile().getName(),clientProtocol,MenuProtocol.VERSION);
                send(p,message("incompatible",clientProtocol<MenuProtocol.VERSION?"Обновите AntHub, чтобы открыть меню этого сервера.":"Эта версия AntHub пока несовместима с меню сервера. Попробуйте подключиться позже."));return;}menuClients.add(p.getUUID());if(j.has("client"))record(p.getUUID(),j.getAsJsonObject("client"));sendState(p,false);return;}
            if(!menuClients.contains(p.getUUID())){send(p,message("incompatible","Сначала требуется согласовать версию AntHub."));return;}
            if(action.equals("community")&&!ServerIntegration.supports(p,Json.opt(j,"section",""))){sendError(p,j,"Раздел недоступен в этой версии AntHub");return;}
            if(action.equals("players")&&!ServerIntegration.supports(p,"players"))return;
            if(action.equals("community")){
                String operation=Json.str(j,"op");if(operation.startsWith("plus")&&!ServerIntegration.supports(p,"community-plus"))throw new IllegalArgumentException("Возможность недоступна");
                if(j.has("location")&&!j.get("location").isJsonNull()){var location=CommunityLocation.read(j.getAsJsonObject("location"));var dimension=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(location.dimension()));if(p.server.getLevel(dimension)==null)throw new IllegalArgumentException("Такого измерения нет на этом сервере");}if(!Set.of("list","detail","plusProfile","plusIgnores","plusItemRead").contains(operation)){UUID.fromString(Json.str(j,"operationId"));if(!j.has("issuedAt"))throw new IllegalArgumentException("Missing command timestamp");}
                var actor=actor(p);var copy=j.deepCopy();var store=community;var selfStats=ServerPlayerStatistics.store();var live=ServerPlayerStatistics.live();var selfId=p.getUUID();
                (Set.of("list","detail","plusProfile","plusIgnores","plusItemRead").contains(operation)?READS:STORAGE).submit(()->{try{var currentActor=p.server.submit(()->{
                    if(!p.connection.getConnection().isConnected()||!AuthServer.authenticated(p))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Сеанс завершён");
                    return actor(p);
                }).get(2,TimeUnit.SECONDS);
                var result=store.request(currentActor,copy);if(Json.opt(copy,"section","").equals("home")&&selfStats!=null){var stats=selfStats.read(List.of(selfId),live);if(stats.containsKey(selfId))result.add("selfStatistics",stats.get(selfId));}p.server.execute(()->{if(community==store&&p.connection.hasChannel(Protocol.FeatureState.TYPE)){if(!currentActor.equals(actor(p))){sendFailure(p,copy,new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Права изменились. Обновите раздел."));return;}if(result.has("groups"))for(var entry:result.getAsJsonArray("groups")){var group=entry.getAsJsonObject();if(group.has("memberIds")){int online=0;for(var id:group.getAsJsonArray("memberIds"))if(p.server.getPlayerList().getPlayer(UUID.fromString(id.getAsString()))!=null)online++;group.addProperty("onlineCount",online);group.remove("memberIds");}}send(p,result);if(!Set.of("list","detail","plusProfile","plusIgnores","plusItemRead").contains(Json.opt(copy,"op","")))invalidate(p.server,Json.opt(copy,"section",""),Json.opt(copy,"id",""));}});}
                catch(Exception ex){p.server.execute(()->{sendFailure(p,copy,ex);});}});return;
            }
            if(action.equals("moderationVote")){if(!ServerIntegration.supports(p,"moderation-votes"))throw new IllegalArgumentException("Возможность недоступна");ServerModerationVotes.request(p,j,result->send(p,result),failure->sendFailure(p,j,failure));return;}
            if(action.equals("players")){
                int page=bounded(j,"page",1000);String query=Json.opt(j,"query","");if(query.length()>32)throw new IllegalArgumentException("Search too long");
                boolean onlineOnly=!j.has("all")||!j.get("all").getAsBoolean();var online=new HashMap<String,String>();var metadata=new HashMap<String,JsonObject>();for(var player:p.server.getPlayerList().getPlayers()){online.put(player.getUUID().toString(),player.getGameProfile().getName());if(ServerIntegration.luckPermsEnabled())metadata.put(player.getUUID().toString(),LuckPermsAdapter.profile(player.getUUID()));}var actions=ServerExtras.actions(p);var store=community;var statsStore=ServerPlayerStatistics.store();var liveStats=ServerPlayerStatistics.live();boolean showStats=ServerIntegration.supports(p,"player-statistics");
                READS.submit(()->{try{var data=message("players","");data.addProperty("page",page);data.addProperty("request",Json.opt(j,"request",""));var rows=new JsonArray();if(onlineOnly){var onlineRows=new JsonArray();String scope="online:"+query,after=PlayerCursor.after(Json.opt(j,"cursor",""),scope);online.entrySet().stream().filter(x->x.getKey().compareTo(after)>0&&x.getValue().toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))).sorted(Map.Entry.comparingByKey()).limit(21).forEach(x->{var row=new JsonObject();row.addProperty("uuid",x.getKey());row.addProperty("name",x.getValue());row.addProperty("online",true);onlineRows.add(row);});var batch=PlayerCursor.page(onlineRows,scope);rows=batch.getAsJsonArray("entries");data.add("nextCursor",batch.get("nextCursor"));}else{var batch=store.peopleCursor(query,Json.opt(j,"cursor",""));rows=batch.getAsJsonArray("entries");data.add("nextCursor",batch.get("nextCursor"));for(var row:rows)row.getAsJsonObject().addProperty("online",online.containsKey(Json.str(row.getAsJsonObject(),"uuid")));}for(var element:rows){var row=element.getAsJsonObject();var meta=metadata.get(Json.str(row,"uuid"));if(meta!=null){row.addProperty("prefix",Json.opt(meta,"prefix",""));row.addProperty("suffix",Json.opt(meta,"suffix",""));}}if(showStats&&statsStore!=null){var ids=new ArrayList<UUID>();for(var row:rows)ids.add(UUID.fromString(Json.str(row.getAsJsonObject(),"uuid")));var stats=statsStore.read(ids,liveStats);for(var row:rows){var item=row.getAsJsonObject();var view=stats.get(UUID.fromString(Json.str(item,"uuid")));if(view!=null)item.add("statistics",view);}}store.profiles(rows);data.add("players",rows);data.add("actions",actions);p.server.execute(()->send(p,data));}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});return;
            }
            if(action.equals("moderate")){UUID.fromString(Json.str(j,"operationId"));ServerExtras.moderate(p,j);invalidate(p.server);return;}
            if(action.equals("report")){UUID.fromString(Json.str(j,"operationId"));var store=reports;var copy=j.deepCopy();STORAGE.submit(()->{try{var receipt=store.submitRequest(p.getUUID(),p.getGameProfile().getName(),copy,System.currentTimeMillis());p.server.execute(()->{receipt.addProperty("kind","result");receipt.addProperty("request",Json.opt(copy,"request",""));send(p,receipt);invalidate(p.server);});}catch(Exception ex){p.server.execute(()->sendFailure(p,j,ex));}});return;}
            if(action.equals("menuData")){var data=message("menuData","");data.addProperty("request",Json.opt(j,"request",""));data.add("menu",ServerExtras.menu());send(p,data);return;}
            if(action.equals("myReport")){String id=Json.str(j,"id");var store=reports;READS.submit(()->{try{var data=message("myReports","");data.addProperty("page",0);data.addProperty("request",Json.opt(j,"request",""));var entries=new JsonArray();entries.add(store.get(p.getUUID(),id));data.add("reports",entries);p.server.execute(()->send(p,data));}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});return;}
            if(action.equals("myReports")){int page=bounded(j,"page",1000);READS.submit(()->{try{var data=message("myReports","");data.addProperty("page",page);data.addProperty("request",Json.opt(j,"request",""));var batch=reports.cursor(p.getUUID(),Json.opt(j,"cursor",""));data.add("reports",batch.get("entries"));data.add("nextCursor",batch.get("nextCursor"));p.server.execute(()->send(p,data));}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});return;}
            if(!allowedAction(p,action))throw new IllegalArgumentException("Недостаточно прав");
            switch(action){
                case "playerAdministration" -> {
                    if(!ServerIntegration.supports(p,"admin-tools"))throw new IllegalArgumentException("Возможность недоступна");
                    UUID targetId=UUID.fromString(Json.str(j,"target"));var target=p.server.getPlayerList().getPlayer(targetId);var data=message("playerAdministration","");data.addProperty("request",Json.opt(j,"request",""));data.addProperty("online",target!=null);
                    data.add("permissions",ServerIntegration.luckPermsEnabled()?LuckPermsAdapter.profile(targetId):new JsonObject());data.add("actions",target==null?new JsonArray():ServerExtras.actions(target));String cursor=Json.opt(j,"cursor","");var store=community;
                    READS.execute(()->{try{data.add("history",store.recordCursor("moderation-history",targetId.toString(),cursor,10));p.server.execute(()->{if(p.connection.getConnection().isConnected()&&AuthServer.authenticated(p)&&admin(p))send(p,data);});}catch(Exception failure){p.server.execute(()->sendFailure(p,j,failure));}});
                }
                case "pinAnnouncement","exportCommunity" -> {
                    if(!ServerIntegration.supports(p,"admin-tools"))throw new IllegalArgumentException("Возможность недоступна");var copy=j.deepCopy();var db=ServerDatabase.get();var store=community;
                    CONTROL.execute(()->{try{
                        if(!p.server.submit(()->p.connection.getConnection().isConnected()&&AuthServer.authenticated(p)&&allowedAction(p,action)).get(2,TimeUnit.SECONDS))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Недостаточно прав");
                        var administration=new CommunityAdministration(db,store);String answer;
                        if(action.equals("pinAnnouncement")){administration.pin(p.getGameProfile().getName(),Json.str(copy,"text"),bounded(copy,"minutes",10080));answer="Объявление сохранено";long expiry=copy.get("text").getAsString().isBlank()?0:System.currentTimeMillis()+copy.get("minutes").getAsLong()*60000;p.server.execute(()->{pinnedUntil=expiry;pinnedText=Json.str(copy,"text");for(var player:p.server.getPlayerList().getPlayers())sendState(player,false);});}
                        else{var path=administration.export(settings.resolve("exports"),p.getGameProfile().getName());answer="Экспорт сохранён на сервере: anthub/exports/"+path.getFileName();}
                        p.server.execute(()->{if(allowedAction(p,action))send(p,message("result",answer));invalidate(p.server,"home","");});
                    }catch(Exception failure){p.server.execute(()->sendFailure(p,copy,failure));}});
                }
                case "reloadMenu" -> {throw new IllegalArgumentException("Настройки anthub-server.toml применяются после перезапуска сервера");}
                case "history" -> {int page=bounded(j,"page",100);READS.submit(()->{try{var data=message("history","");data.addProperty("page",page);data.addProperty("request",Json.opt(j,"request",""));var batch=community.recordCursor("audit","",Json.opt(j,"cursor",""),10);data.add("entries",batch.get("entries"));data.add("nextCursor",batch.get("nextCursor"));p.server.execute(()->{if(admin(p))send(p,data);});}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});}
                case "reply" -> {UUID.fromString(Json.str(j,"operationId"));String id=Json.str(j,"id"),text=Json.str(j,"text");boolean resolved=j.get("resolved").getAsBoolean();var store=reports;String author=p.getGameProfile().getName();STORAGE.submit(()->{try{if(!p.server.submit(()->p.connection.getConnection().isConnected()&&AuthServer.authenticated(p)&&allowedAction(p,action)).get(2,TimeUnit.SECONDS))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Недостаточно прав");var receipt=store.replyRequest(p.getUUID().toString(),author,j);p.server.execute(()->{receipt.addProperty("kind","result");receipt.addProperty("request",Json.opt(j,"request",""));send(p,receipt);invalidate(p.server);});}catch(Exception ex){p.server.execute(()->sendFailure(p,j,ex));}});}

                case "diagnostics" -> {
                    var snapshot=diagnostics(p.server);snapshot.addProperty("version",dev.abros.anthub.AntHub.VERSION);snapshot.add("recentErrors",failures.snapshot());snapshot.addProperty("luckPerms",ServerIntegration.luckPermsEnabled());snapshot.addProperty("plasmoVoice",net.neoforged.fml.ModList.get().isLoaded("plasmovoice"));
                    READS.execute(()->{long began=System.nanoTime();try{ServerDatabase.get().transaction(()->{try(var q=ServerDatabase.get().connection().createStatement()){q.execute("SELECT 1");}return null;});snapshot.addProperty("database","Доступна");snapshot.addProperty("databaseMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began));}catch(Exception failure){snapshot.addProperty("database","Недоступна");}p.server.execute(()->{if(p.connection.getConnection().isConnected()&&AuthServer.authenticated(p)&&allowedAction(p,action))send(p,snapshot);});});
                }
                case "reports" -> {int page=j.get("page").getAsInt();var store=reports;STORAGE.submit(()->{try{var result=message("reports","");result.addProperty("page",page);result.addProperty("request",Json.opt(j,"request",""));var batch=store.cursor(null,Json.opt(j,"cursor",""));result.add("reports",batch.get("entries"));result.add("nextCursor",batch.get("nextCursor"));p.server.execute(()->{if(allowedAction(p,action))send(p,result);});}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});}
                case "announce" -> {String text=Json.str(j,"text").strip();if(text.isEmpty()||text.length()>500)throw new IllegalArgumentException("Message must contain 1–500 characters");announcement(p.server,text);}
                case "maintenance" -> maintenance(p.server,j.get("enabled").getAsBoolean(),bounded(j,"minutes",1440),Json.str(j,"text"),p.createCommandSourceStack());
                case "restart" -> restart(p.server,bounded(j,"seconds",86400),Json.opt(j,"text",""),p.createCommandSourceStack());
                default -> throw new IllegalArgumentException("Unknown action");
            }
            if(Set.of("announce","reloadMenu").contains(action)){auditAsync(p.getGameProfile().getName(),action,Json.GSON.toJson(j));send(p,message("result",Set.of("maintenance","restart").contains(action)?"Change queued; confirmation follows after saving":"Action completed"));}
        }catch(RejectedExecutionException busy){sendError(p,j,"Server storage is busy; try again shortly");}catch(Exception ex){sendFailure(p,j,ex);}
    }
    private static void sendFailure(ServerPlayer p,JsonObject request,Exception ex){
        String code=ex instanceof CommunityFailure failure?failure.code().name():ex instanceof IllegalArgumentException?"INVALID":"UNAVAILABLE";
        String diagnostic=UUID.randomUUID().toString();
        if(code.equals("UNAVAILABLE")){var incident=failures.record(Json.opt(request,"action","request"),ex,System.currentTimeMillis());diagnostic=incident.id();if(incident.log())com.mojang.logging.LogUtils.getLogger().error("AntHub request failed [{}] action={} type={} count={}",diagnostic,incident.operation(),incident.type(),incident.count());}
        var result=message(Json.opt(request,"action","").equals("community")?"community":"result",safeError(ex));result.addProperty("request",Json.opt(request,"request",""));result.addProperty("error",true);result.addProperty("code",code);result.addProperty("recovery",code.equals("CONFLICT")?"refresh":code.equals("INVALID")?"edit":code.equals("FORBIDDEN")?"close":"retry");result.addProperty("diagnosticId",diagnostic);send(p,result);
    }
    private static String safeError(Exception ex){return ex instanceof IllegalArgumentException?ex.getMessage():"Хранилище временно недоступно. Повторите запрос позже.";}
    private static void sendError(ServerPlayer p,JsonObject request,String text){var result=message(Json.opt(request,"action","").equals("community")?"community":"result",text);result.addProperty("request",Json.opt(request,"request",""));result.addProperty("error",true);send(p,result);}
    private static String role(ServerPlayer p){return ServerIntegration.luckPermsEnabled()?Json.opt(LuckPermsAdapter.profile(p.getUUID()),"primaryGroup","Игрок"):admin(p)?"Администратор":"Игрок";}
    private static CommunityStore.Actor actor(ServerPlayer p){return new CommunityStore.Actor(p.getUUID().toString(),p.getGameProfile().getName(),admin(p),p.hasPermissions(2)||ServerIntegration.luckPermsEnabled()&&LuckPermsAdapter.profile(p.getUUID()).getAsJsonObject("capabilities").get("anthub.events").getAsBoolean());}
    private static int bounded(JsonObject j,String key,int max){int value=j.get(key).getAsInt();if(value<0||value>max)throw new IllegalArgumentException("Invalid "+key);return value;}
    private interface Work {void run(CommandSourceStack source)throws Exception;}
    private static int execute(CommandSourceStack source,String command,Work work){try{work.run(source);if(!command.matches("/?ah (maintenance|restart)( .*|$)"))auditAsync(source.getTextName(),"/ah", command);return 1;}catch(Exception ex){source.sendFailure(Component.literal(safeError(ex)));return 0;}}
    private static boolean admin(CommandSourceStack source){return source.hasPermission(2)||source.getEntity() instanceof ServerPlayer p&&admin(p);}
    private static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){
        ServerCommands.register(e.getDispatcher());
        var announce=Commands.literal("announce").requires(s->ServerCommands.allowed(s,"anthub.announce"))
            .then(Commands.literal("pin").then(Commands.argument("minutes",IntegerArgumentType.integer(1,10080)).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("15","60","1440"),b)).then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->pin(c.getSource(),StringArgumentType.getString(c,"text"),IntegerArgumentType.getInteger(c,"minutes"))))))
            .then(Commands.literal("unpin").executes(c->pin(c.getSource(),"",1)))
            .then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->execute(c.getSource(),c.getInput(),s->{String text=StringArgumentType.getString(c,"text").strip();if(text.isEmpty()||text.length()>500)throw new IllegalArgumentException("Сообщение: 1–500 символов");announcement(s.getServer(),text);ServerCommands.reply(s,"Объявление отправлено");})));
        e.getDispatcher().register(Commands.literal("ah").executes(c->execute(c.getSource(),c.getInput(),s->{var p=s.getPlayerOrException();if(!p.connection.hasChannel(Protocol.FeatureState.TYPE))throw new IllegalArgumentException("Установите AntHub для открытия меню");sendState(p,true);}))
            .then(communityLinkCommand("share",true)).then(communityLinkCommand("open",false))
            .then(Commands.literal("status").requires(s->ServerCommands.allowed(s,"anthub.diagnostics")).executes(c->status(c.getSource())))
            .then(announce)
            .then(Commands.literal("maintenance").requires(s->ServerCommands.allowed(s,"anthub.maintenance"))
                .then(Commands.literal("off").executes(c->execute(c.getSource(),c.getInput(),s->maintenance(s.getServer(),false,0,"",s))))
                .then(Commands.literal("on").then(Commands.argument("minutes",IntegerArgumentType.integer(0,1440)).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("0","15","30","60"),b)).then(Commands.argument("reason",StringArgumentType.greedyString()).executes(c->execute(c.getSource(),c.getInput(),s->maintenance(s.getServer(),true,IntegerArgumentType.getInteger(c,"minutes"),StringArgumentType.getString(c,"reason"),s)))))))
            .then(Commands.literal("restart").requires(s->ServerCommands.allowed(s,"anthub.restart"))
                .then(Commands.literal("cancel").executes(c->execute(c.getSource(),c.getInput(),s->restart(s.getServer(),0,"",s))))
                .then(Commands.argument("seconds",IntegerArgumentType.integer(1,86400)).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("60","300","600"),b)).then(Commands.argument("reason",StringArgumentType.greedyString()).executes(c->execute(c.getSource(),c.getInput(),s->restart(s.getServer(),IntegerArgumentType.getInteger(c,"seconds"),StringArgumentType.getString(c,"reason"),s))))))
            .then(Commands.literal("reports").requires(s->ServerCommands.allowed(s,"anthub.reports")).executes(c->{var source=c.getSource();try{READS.execute(()->{try{var rows=reports.list(0);var lines=new ArrayList<String>();for(var item:rows){var j=item.getAsJsonObject();String text=Json.str(j,"message").replace('\n',' ');lines.add(Json.str(j,"id")+" · "+Json.str(j,"player")+" · "+(Json.str(j,"status").equals("open")?"Открыто":"Закрыто")+"\n"+text.substring(0,Math.min(160,text.length())));}source.getServer().execute(()->{if(ServerCommands.allowed(source,"anthub.reports"))ServerCommands.reply(source,lines.isEmpty()?"Обращений нет":String.join("\n\n",lines)+"\nОтветы и остальные обращения — в меню AntHub.");});}catch(Exception ex){source.getServer().execute(()->ServerCommands.error(source,ex));}});return 1;}catch(Exception ex){ServerCommands.error(source,ex);return 0;}})));
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> communityLinkCommand(String name,boolean broadcast){
        return Commands.literal(name).then(Commands.argument("section",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("board","groups","events","polls","ideas"),b)).then(Commands.argument("id",StringArgumentType.word()).executes(c->{var source=c.getSource();try{var player=source.getPlayerOrException();if(!AuthServer.authenticated(player)||!ServerIntegration.supports(player,"community-plus"))throw new IllegalArgumentException("Действие недоступно");String section=StringArgumentType.getString(c,"section"),id=StringArgumentType.getString(c,"id");UUID.fromString(id);String key=player.getUUID()+":share";long now=System.currentTimeMillis();if(now-requests.getOrDefault(key,0L)<(broadcast?5000:500))throw new IllegalArgumentException("Подождите перед повторной отправкой");requests.put(key,now);var sender=actor(player);var recipients=new java.util.LinkedHashMap<UUID,CommunityStore.Actor>();for(var recipient:source.getServer().getPlayerList().getPlayers())if(AuthServer.authenticated(recipient)&&ServerIntegration.supports(recipient,"community-plus")&&(broadcast||recipient==player))recipients.put(recipient.getUUID(),actor(recipient));var store=community;
            READS.execute(()->{try{String title=store.sharedEntry(sender,section,id);var allowed=new java.util.HashSet<UUID>();for(var recipient:recipients.entrySet())try{store.sharedEntry(recipient.getValue(),section,id);allowed.add(recipient.getKey());}catch(CommunityFailure denied){}
                source.getServer().execute(()->{for(var target:allowed){var recipient=source.getServer().getPlayerList().getPlayer(target);if(recipient==null||!AuthServer.authenticated(recipient))continue;if(broadcast)recipient.sendSystemMessage(Component.literal(sender.name()+" · ").append(Component.literal("["+title+"]").withStyle(style->style.withColor(0x82B6F2).withUnderlined(true).withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,"/ah open "+section+" "+id)))));else{var packet=message("openCommunity","");packet.addProperty("section",section);packet.addProperty("id",id);send(recipient,packet);}}});
            }catch(Exception ex){source.getServer().execute(()->ServerCommands.error(source,ex));}});return 1;}catch(Exception ex){ServerCommands.error(source,ex);return 0;}})));
    }
    private static int pin(CommandSourceStack source,String text,int minutes){try{CONTROL.execute(()->{try{if(!source.getServer().submit(()->ServerCommands.allowed(source,"anthub.announce")).get(2,TimeUnit.SECONDS))throw new IllegalArgumentException("Права изменились");new CommunityAdministration(ServerDatabase.get(),community).pin(source.getTextName(),text,minutes);source.getServer().execute(()->{pinnedUntil=text.isBlank()?0:System.currentTimeMillis()+minutes*60000L;pinnedText=text;for(var player:source.getServer().getPlayerList().getPlayers())sendState(player,false);ServerCommands.reply(source,text.isBlank()?"Закрепление снято":"Объявление закреплено");invalidate(source.getServer(),"home","");});}catch(Exception ex){source.getServer().execute(()->ServerCommands.error(source,ex));}});return 1;}catch(Exception ex){ServerCommands.error(source,ex);return 0;}}
    private static int status(CommandSourceStack source){
        String base="AntHub "+dev.abros.anthub.AntHub.VERSION+"\nAuth: "+(AuthServer.enabled()?"включён":"выключен")+"\nLuckPerms: "+(ServerIntegration.luckPermsEnabled()?"интеграция включена":"интеграция выключена")+"\nPlasmo Voice: "+(net.neoforged.fml.ModList.get().isLoaded("plasmovoice")?"установлен":"не установлен")+"\nИгроков: "+source.getServer().getPlayerCount()+"\nПроект: "+ServerIntegration.project()+"\nСборка: "+ServerIntegration.packVersion();
        try{READS.execute(()->{String database;try{ServerDatabase.get().transaction(()->{try(var q=ServerDatabase.get().connection().createStatement()){q.execute("SELECT 1");}return null;});database="Доступна";}catch(Exception ex){database="Недоступна";}String text=base+"\nБаза данных: "+database;source.getServer().execute(()->{if(ServerCommands.allowed(source,"anthub.diagnostics"))ServerCommands.reply(source,text);});});return 1;}catch(Exception ex){ServerCommands.error(source,ex);return 0;}
    }
}
