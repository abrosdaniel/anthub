package dev.abros.anthub.server;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.abros.anthub.core.Json;
import dev.abros.anthub.core.auth.*;
import net.minecraft.commands.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.*;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class AuthServer {
    private static final ConfigurationTask.Type TASK=new ConfigurationTask.Type(ResourceLocation.fromNamespaceAndPath("anthub","auth"));
    private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"AntHub auth");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static final ExecutorService OFFICIAL=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"AntHub official verification");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static final ScheduledExecutorService DEADLINES=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"AntHub auth deadlines");t.setDaemon(true);return t;});
    private static final ExecutorService VALIDATION=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var t=new Thread(r,"AntHub session checks");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static final AtomicBoolean validating=new AtomicBoolean();
    private static final Map<Connection,Session> SESSIONS=new ConcurrentHashMap<>();
    private static volatile AuthStore store;private static AuthTls.Identity identity;private static MinecraftServer server;private static Path root;private static volatile String mode="false";private static long lastCheck;
    private static volatile ServerIdentities identities;
    /** Cache lookups also serve synthetic mod profiles; only player profiles are remapped. */
    public static com.mojang.authlib.GameProfile lookupProfile(com.mojang.authlib.GameProfile original){
        if(original==null||original.getId()==null||!AuthStore.validName(original.getName()))return original;
        return profile(original);
    }
    public static com.mojang.authlib.GameProfile profile(com.mojang.authlib.GameProfile original){
        return profile(original,null);
    }
    public static com.mojang.authlib.GameProfile profile(com.mojang.authlib.GameProfile original,UUID claimedOfficial){
        var index=identities;if(!enabled()||index==null)return original;var identity=index.resolve(original.getName(),original.getId(),claimedOfficial);
        var result=new com.mojang.authlib.GameProfile(identity.uuid(),identity.name());result.getProperties().putAll(original.getProperties());return result;
    }
    private static void remember(AuthStore.Account account){identities.remember(new AuthStore.Profile(account.name(),UUID.fromString(account.uuid()),account.official()==null?null:UUID.fromString(account.official())));}
    public static boolean enabled(){return !mode.equals("false");}
    public static void install(IEventBus bus,ModContainer container){
        bus.addListener(AuthServer::tasks);AuthProtocol.server=AuthServer::receive;AuthProtocol.upgrade=AuthServer::upgrade;
        NeoForge.EVENT_BUS.addListener(AuthServer::start);NeoForge.EVENT_BUS.addListener(AuthServer::tick);NeoForge.EVENT_BUS.addListener(AuthServer::commands);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e)->{SESSIONS.values().forEach(Session::close);SESSIONS.clear();store=null;identity=null;identities=null;mode="false";});
    }
    private static void start(net.neoforged.neoforge.event.server.ServerStartingEvent e){
        server=e.getServer();mode="false";store=null;identity=null;lastCheck=0;
        String requestedMode=ServerDatabase.settings().text("auth.mode");if(requestedMode.equals("false"))return;
        var database=ServerDatabase.get();
        if(ModList.get().isLoaded("authlogic"))throw new IllegalStateException("Remove AuthLogic before enabling AntHub Auth");
        root=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
        try{
            store=new AuthStore(database,ServerDatabase.settings().number("auth.minimumPasswordLength"));identities=new ServerIdentities(store.profiles());identity=AuthTls.identity(root.resolve("anthub"));mode=requestedMode;
            com.mojang.logging.LogUtils.getLogger().info("AntHub Auth TLS fingerprint: {}",identity.fingerprint());
        }catch(Exception ex){throw new IllegalStateException("Cannot initialize AntHub Auth; startup aborted",ex);}
    }
    public static boolean mayReset(ServerPlayer p){return enabled()&&authenticated(p)&&LuckPermsAdapter.profile(p.getUUID()).getAsJsonObject("capabilities").get("anthub.auth.reset").getAsBoolean();}
    public static boolean authenticated(ServerPlayer p){if(!enabled())return true;var s=SESSIONS.get(p.connection.getConnection());return s!=null&&s.joined&&s.account!=null;}
    private static void tasks(RegisterConfigurationTasksEvent e){
        if(!enabled())return;
        if(!(e.getListener() instanceof ServerConfigurationPacketListenerImpl listener))return;
        if(!listener.hasChannel(AuthProtocol.Upgrade.TYPE)||!listener.hasChannel(AuthProtocol.Ready.TYPE)||!listener.hasChannel(AuthProtocol.Hello.TYPE)||!listener.hasChannel(AuthProtocol.ToServer.TYPE)||!listener.hasChannel(AuthProtocol.ToClient.TYPE)){listener.disconnect(Component.literal("Для входа требуется AntHub с модулем Auth"));return;}
        e.register(new ICustomConfigurationTask(){public ConfigurationTask.Type type(){return TASK;}public void run(java.util.function.Consumer<net.minecraft.network.protocol.common.custom.CustomPacketPayload> send){
            if(SESSIONS.values().stream().filter(s->!s.joined).count()>=128||SESSIONS.values().stream().filter(s->!s.joined&&sameAddress(s.connection,listener.getConnection())).count()>=8){listener.disconnect(Component.literal("Слишком много подключений. Попробуйте позже"));return;}
            try{Session session=new Session(listener);SESSIONS.put(listener.getConnection(),session);send.accept(new AuthProtocol.Hello(identity.fingerprint()));}catch(Exception ex){listener.disconnect(Component.literal("Не удалось начать защищённый вход"));}
        }});
    }
    private static void upgrade(IPayloadContext context){
        var session=SESSIONS.get(context.connection());
        if(session==null||session.upgrading){context.disconnect(Component.literal("Unexpected TLS upgrade"));return;}
        session.upgrading=true;
        // Flush the final plaintext marker before inserting TLS at the front of the channel.
        context.connection().channel().writeAndFlush(new ClientboundCustomPayloadPacket(new AuthProtocol.Ready())).addListener(sent->{
            if(!sent.isSuccess()){session.disconnect("TLS upgrade failed");return;}
            AuthTransport.install(session.connection,identity.context(),false).whenComplete((unused,error)->{
                if(error!=null)session.disconnect("TLS transport failed");else session.transportReady=true;
            });
        });
    }
    private static boolean sameAddress(Connection a,Connection b){return a.getRemoteAddress() instanceof java.net.InetSocketAddress x&&b.getRemoteAddress() instanceof java.net.InetSocketAddress y&&Objects.equals(x.getAddress(),y.getAddress());}
    private static void receive(byte[] bytes,IPayloadContext c){
        var s=SESSIONS.get(c.connection());if(s==null||!s.transportReady){c.disconnect(Component.literal("Нет сеанса Auth"));return;}
        long now=System.currentTimeMillis();if(now-s.window>1000){s.window=now;s.packets=0;}if(++s.packets>40){s.disconnect("Слишком много запросов Auth");return;}if(s.queued.incrementAndGet()>4){s.queued.decrementAndGet();s.disconnect("Слишком много запросов Auth");return;}
        // Capture current permission from the game thread; no global admin/OP fallback.
        boolean reset=c.listener() instanceof ServerGamePacketListenerImpl play&&mayReset(play.player);
        try{s.serial.execute(()->{try{if(!s.connection.isConnected())return;s.resetAllowed=reset;s.tunnel.receive(bytes);if(s.tunnel.ready()&&!s.offered){s.offered=true;s.offer();}}catch(Exception ex){s.disconnect("Ошибка защищённого соединения Auth");}finally{s.queued.decrementAndGet();}});}catch(RejectedExecutionException ex){s.queued.decrementAndGet();s.disconnect("Auth занят. Попробуйте позже");}
    }
    private static final class Session {
        final Connection connection;final ServerConfigurationPacketListenerImpl listener;final String name,uuid,challenge=AuthSecrets.token().substring(0,32);final long created=System.currentTimeMillis();final AtomicInteger queued=new AtomicInteger();final AuthStore database=store;
        final dev.abros.anthub.core.SerialExecutor serial=new dev.abros.anthub.core.SerialExecutor(WORK,4);
        boolean officialPending;volatile Future<?> officialRequest;
        final AuthTls.Tunnel tunnel;volatile AuthStore.Account account;volatile boolean joined,transportReady;boolean upgrading;boolean offered,resetAllowed;volatile String device="";long window;int packets,attempts;long lastAction;
        Session(ServerConfigurationPacketListenerImpl listener)throws Exception{this.listener=listener;connection=listener.getConnection();name=AuthStore.name(listener.getOwner().getName());uuid=listener.getOwner().getId().toString();tunnel=new AuthTls.Tunnel(identity.context(),false,b->connection.send(new ClientboundCustomPayloadPacket(new AuthProtocol.ToClient(b))),this::message);}
        void send(JsonObject j)throws Exception{tunnel.send(Json.GSON.toJson(j));}
        void offer()throws Exception{var j=result("offer","");j.addProperty("mode",mode);j.addProperty("name",name);j.addProperty("challenge",challenge);var a=database.account(name);j.addProperty("type",a==null?"new":a.type());j.addProperty("linked",a!=null&&a.official()!=null);j.addProperty("registration",ServerDatabase.settings().flag("auth.allowRegistration"));j.addProperty("minimumPasswordLength",database.minimumPasswordLength());send(j);}
        void message(String json){message(json,null);}
        void message(String json,String verified){try{
            var j=Json.parse(json);String action=Json.str(j,"action");long now=System.currentTimeMillis();
            if(officialPending&&verified==null)throw new IllegalArgumentException("Подождите подтверждения Minecraft-аккаунта");
            if(action.equals("accept")){if(joined||account==null)throw new IllegalArgumentException("Unexpected auth confirmation");validate();joined=true;server.execute(()->{if(connection.isConnected())listener.finishCurrentTask(TASK);});return;}
            if(verified==null&&now-lastAction<700)throw new IllegalArgumentException("Подождите перед следующим действием");lastAction=now;
            if(verified==null&&(action.equals("official")||action.equals("link"))){
                if(!mode.equals("hybrid")||action.equals("link")&&!joined||action.equals("official")&&joined)throw new IllegalArgumentException("Операция недоступна");
                if(++attempts>10){disconnect("Слишком много попыток входа");return;}database.checkRate(name,now);
                verifyLater(json,j,action.equals("link")?"link":"login");return;
            }
            if(!joined){
                if(account!=null)throw new IllegalArgumentException("Ожидается подтверждение входа");if(verified==null){if(++attempts>10){disconnect("Слишком много попыток входа");return;}database.checkRate(name,now);}
                switch(action){
                    case "login" -> account=withPassword(j,"password",p->database.login(name,uuid,p,now));
                    case "register" -> {if(!ServerDatabase.settings().flag("auth.allowRegistration"))throw new IllegalArgumentException("Регистрация закрыта. Обратитесь к администратору");account=withPassword(j,"password",p->database.register(name,uuid,p,now));}
                    case "device" -> {String token=Json.str(j,"token");account=database.deviceLogin(name,uuid,token,now);device=database.deviceId(name,token);}
                    case "official" -> {
                        if(!mode.equals("hybrid"))throw new IllegalArgumentException("Официальный вход недоступен");
                        account=database.official(name,uuid,verified,now);
                    }
                    case "reset" -> {withPassword(j,"password",p->{database.reset(name,uuid,Json.str(j,"invitation"),p,now);return null;});remember(database.account(name));invalidate(name);send(result("resetDone","Пароль изменён. Войдите с новым паролем"));return;}
                    default -> throw new IllegalArgumentException("Unexpected auth operation");
                }
                remember(account);var out=result("authenticated","");out.addProperty("type",account.type());
                if(account.type().equals("local")&&j.has("remember")&&j.get("remember").getAsBoolean()&&!action.equals("device")){var saved=database.remember(name,Json.opt(j,"label","Мой компьютер"),now);device=saved.id();out.addProperty("token",saved.token());}
                send(out);return;
            }
            validate();switch(action){
                case "devices" -> {var out=result("devices","");out.addProperty("type",account.type());out.addProperty("current",device);out.addProperty("linked",account.official()!=null);out.addProperty("mode",mode);out.add("devices",database.devices(name,now));send(out);}
                case "link" -> {
                    String official=verified;
                    account=withPassword(j,"password",p->database.linkOfficial(name,uuid,official,p,now));device="";remember(account);
                    invalidate(name);send(result("updated","Minecraft-аккаунт привязан. Остальные сеансы отозваны"));
                }
                case "unlink" -> {
                    account=withPassword(j,"password",p->database.unlinkOfficial(name,uuid,p,now));device="";remember(account);
                    invalidate(name);send(result("updated","Minecraft-аккаунт отвязан. Остальные сеансы отозваны"));
                }
                case "change" -> {withPassword(j,"oldPassword",old->withPassword(j,"password",p->{database.change(name,uuid,old,p,now);return null;}));invalidate(name);send(result("changed","Пароль изменён. Войдите заново"));disconnect("Пароль изменён. Подключитесь заново");}
                case "revoke" -> {database.revoke(name,Json.str(j,"device"),now);invalidate(name);send(result("updated","Доступ устройства отозван"));}
                case "invite" -> {
                    if(!resetAllowed)throw new IllegalArgumentException("Требуется право anthub.auth.reset");
                    String target=AuthStore.name(Json.str(j,"target"));String token=database.invite(name,target,now);
                    // Permission can be revoked while the database work is queued. Recheck before disclosing.
                    server.execute(()->{var player=server.getPlayerList().getPlayer(UUID.fromString(uuid));if(player!=null&&player.connection.getConnection()==connection&&mayReset(player))try{WORK.execute(()->{try{var out=result("invitation","");out.addProperty("target",target);out.addProperty("token",token);out.addProperty("expires",now+900000);send(out);}catch(Exception ex){disconnect("Не удалось выдать приглашение");}});}catch(RejectedExecutionException ignored){}});
                }
                default -> throw new IllegalArgumentException("Unknown auth operation");
            }
        }catch(Exception ex){try{send(result("error",ex instanceof IllegalArgumentException?ex.getMessage():"Не удалось выполнить действие Auth. Попробуйте позже"));}catch(Exception ignored){disconnect("Ошибка Auth");}}}
        void verifyLater(String json,JsonObject request,String purpose)throws Exception{
            String launcherName=AuthStore.name(Json.str(request,"officialName"));
            String digest=proof(challenge,identity.fingerprint(),purpose);var service=server.getSessionService();
            officialPending=true;
            var result=new CompletableFuture<String>();
            try{officialRequest=OFFICIAL.submit(()->{try{
                var profile=service.hasJoinedServer(launcherName,digest,null);
                if(profile==null||!launcherName.equalsIgnoreCase(profile.profile().getName()))throw new IllegalArgumentException("Minecraft account not verified");
                result.complete(profile.profile().getId().toString());
            }catch(Exception failure){result.completeExceptionally(failure);}});}catch(RejectedExecutionException busy){officialPending=false;throw new IllegalArgumentException("Проверка Minecraft занята. Попробуйте позже");}
            var timeout=DEADLINES.schedule(()->{if(result.completeExceptionally(new TimeoutException()))officialRequest.cancel(true);},10,TimeUnit.SECONDS);
            result.whenComplete((uuid,error)->{timeout.cancel(false);try{serial.execute(()->{
                officialPending=false;if(!connection.isConnected())return;
                if(error==null)message(json,uuid);else try{database.failure(name,System.currentTimeMillis());send(result("error","Не удалось подтвердить Minecraft-аккаунт. Попробуйте позже"));}catch(Exception failure){disconnect("Ошибка проверки Minecraft-аккаунта");}
            });}catch(RejectedExecutionException busy){disconnect("Auth занят. Попробуйте позже");}});
        }
        void validate()throws Exception{var current=database.account(name);if(current==null||current.blocked()||current.generation()!=account.generation()||!device.isEmpty()&&!database.hasDevice(name,device,System.currentTimeMillis())){disconnect("Доступ отозван. Войдите заново");throw new IllegalArgumentException("Доступ отозван");}}
        void disconnect(String text){joined=false;server.execute(()->connection.disconnect(Component.literal(text)));}
        void close(){var request=officialRequest;if(request!=null)request.cancel(true);tunnel.close();account=null;}
    }
    private static String proof(String challenge,String fingerprint,String purpose){return AuthSecrets.digest("anthub-auth-"+dev.abros.anthub.core.WireProtocols.version("auth")+"\n"+purpose+"\n"+fingerprint+"\n"+challenge).substring(0,40);}
    private interface PasswordWork<T>{T run(char[] password)throws Exception;}
    private static <T>T withPassword(JsonObject j,String field,PasswordWork<T> work)throws Exception{String value=Json.str(j,field);if(value.length()>128)throw new IllegalArgumentException("Пароль слишком длинный");char[] password=value.toCharArray();j.remove(field);try{return work.run(password);}finally{Arrays.fill(password,'\0');}}
    private static JsonObject result(String kind,String text){var j=new JsonObject();j.addProperty("kind",kind);j.addProperty("text",text==null?"":text);return j;}
    private static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){if(!enabled()||System.currentTimeMillis()-lastCheck<1000)return;lastCheck=System.currentTimeMillis();for(var s:SESSIONS.values()){
        if(!s.connection.isConnected()){SESSIONS.remove(s.connection,s);try{s.serial.execute(s::close);}catch(RejectedExecutionException ignored){}continue;}
        if(!s.joined&&lastCheck-s.created>180000){s.disconnect("Время входа истекло");continue;}
    }
        var checks=new HashMap<Session,AuthStore.SessionKey>();for(var session:SESSIONS.values()){var account=session.account;if(session.joined&&account!=null)checks.put(session,new AuthStore.SessionKey(session.name,account.generation(),session.device));}
        if(!checks.isEmpty()&&validating.compareAndSet(false,true)){var database=store;try{VALIDATION.execute(()->{try{var valid=database.validSessions(checks.values(),System.currentTimeMillis());checks.forEach((session,key)->{if(!valid.contains(key))session.disconnect("Доступ отозван. Войдите заново");});}catch(Exception ex){checks.keySet().forEach(session->session.disconnect("Не удалось подтвердить сеанс Auth"));}finally{validating.set(false);}});}catch(RejectedExecutionException busy){validating.set(false);checks.keySet().forEach(session->session.disconnect("Не удалось подтвердить сеанс Auth. Подключитесь заново"));}}
    }
    // Called on the auth worker immediately after committing a credential change.
    private static void invalidate(String name){for(var session:SESSIONS.values())if(session.account!=null&&session.name.equalsIgnoreCase(name))try{session.validate();}catch(Exception ex){session.disconnect("Доступ отозван. Войдите заново");}}
    private static boolean console(CommandSourceStack s){return s.getEntity()==null&&s.hasPermission(4);}
    private static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){
        var reset=Commands.literal("reset").requires(s->enabled()&&(console(s)||s.getEntity() instanceof ServerPlayer p&&mayReset(p)))
            .then(Commands.argument("player",StringArgumentType.word()).executes(c->{var source=c.getSource();String target=StringArgumentType.getString(c,"player");if(source.getEntity() instanceof ServerPlayer p){var session=SESSIONS.get(p.connection.getConnection());try{WORK.execute(()->{try{String token=store.invite(p.getGameProfile().getName(),AuthStore.name(target),System.currentTimeMillis());server.execute(()->{if(mayReset(p)&&session!=null&&p.connection.getConnection()==session.connection)try{WORK.execute(()->{try{var out=result("invitation","");out.addProperty("target",target);out.addProperty("token",token);out.addProperty("expires",System.currentTimeMillis()+900000);session.send(out);}catch(Exception ex){session.disconnect("Ошибка Auth");}});}catch(RejectedExecutionException ignored){}});}catch(Exception ex){server.execute(()->source.sendFailure(Component.literal("Не удалось создать приглашение для локального аккаунта")));}});return 1;}catch(RejectedExecutionException busy){source.sendFailure(Component.literal("Auth занят"));return 0;}}return consoleJob(source,()->{String token=store.invite("console",target,System.currentTimeMillis());Path dir=root.resolve("anthub/auth-invitations");Files.createDirectories(dir);Path file=dir.resolve(AuthStore.name(target)+".txt");Path temp=Files.createTempFile(dir,"invite-",".tmp");try{AuthTls.privateFile(temp);Files.writeString(temp,token);Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}return "Приглашение на 15 минут записано в "+file+". Передайте владельцу лично; удалите файл после передачи.";});}));
        var auth=Commands.literal("auth").then(reset);
        for(boolean block:List.of(true,false))auth.then(Commands.literal(block?"block":"unblock").requires(s->enabled()&&console(s)).then(Commands.argument("player",StringArgumentType.word()).executes(c->consoleJob(c.getSource(),()->{store.block("console",StringArgumentType.getString(c,"player"),block,System.currentTimeMillis());invalidate(StringArgumentType.getString(c,"player"));return "Состояние аккаунта обновлено";}))));
        auth.then(Commands.literal("reserve").requires(s->enabled()&&console(s)).then(Commands.argument("player",StringArgumentType.word()).executes(c->consoleJob(c.getSource(),()->{String name=AuthStore.name(StringArgumentType.getString(c,"player"));store.reserve(name,identities.resolve(name).uuid().toString());remember(store.account(name));return "Имя закреплено. Создайте приглашение командой ah auth reset "+name;}))));
        e.getDispatcher().register(Commands.literal("ah").then(auth));
    }
    private interface CommandWork{String run()throws Exception;}
    private static int consoleJob(CommandSourceStack source,CommandWork work){try{WORK.execute(()->{try{String message=work.run();server.execute(()->source.sendSuccess(()->Component.literal(message),false));}catch(Exception ex){server.execute(()->source.sendFailure(Component.literal(ex instanceof IllegalArgumentException?ex.getMessage():"Ошибка Auth")));}});return 1;}catch(RejectedExecutionException ex){source.sendFailure(Component.literal("Auth занят"));return 0;}}
}
