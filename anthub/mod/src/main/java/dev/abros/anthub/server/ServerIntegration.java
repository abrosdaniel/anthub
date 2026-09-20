package dev.abros.anthub.server;

import com.google.gson.*;
import dev.abros.anthub.core.*;
import dev.abros.anthub.network.Protocol;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class ServerIntegration {
    private static volatile ServerProjectPolicy policy;
    private static final ConfigurationTask.Type TYPE=new ConfigurationTask.Type(ResourceLocation.fromNamespaceAndPath("anthub","verify_pack"));
    private record Pending(String nonce,ServerProjectPolicy policy){}
    private static final Map<Object,Pending> NONCES=Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<Object,Set<String>> FEATURES=Collections.synchronizedMap(new WeakHashMap<>());
    public static boolean supports(net.minecraft.server.level.ServerPlayer player,String feature){return FEATURES.getOrDefault(player.connection.getConnection(),Set.of()).contains(feature);}
    public static void install(IEventBus bus,ModContainer container){
        bus.addListener(ServerIntegration::tasks);
        ServerDatabase.install();AuthServer.install(bus,container);ServerFeatures.install();ServerUpdateNotice.install();
        NeoForge.EVENT_BUS.addListener(ServerIntegration::starting);
        NeoForge.EVENT_BUS.addListener(ServerIntegration::commands);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event)->{policy=null;NONCES.clear();FEATURES.clear();});
        Protocol.serverReply=ServerIntegration::reply;
    }
    private static void starting(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event){
        policy=null;NONCES.clear();FEATURES.clear();
        try {
            var root=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
            var remote=new Remote();remote.cacheMetadata(root.resolve("anthub/cache/http"));
            policy=ServerProjectPolicy.load(new RepositoryClient(root,remote),ServerDatabase.settings().text("project.repository"),ServerDatabase.settings().flag("project.requireProjectPack"));
            if(policy.release()!=null&&!Versions.supportsBranch(dev.abros.anthub.AntHub.VERSION,policy.requiredAntHubVersion())){
                com.mojang.logging.LogUtils.getLogger().error("AntHub: проект требует версию {}, на сервере установлена {}. Установите указанную версию AntHub или исправьте anthubVersion в проекте.",policy.requiredAntHubVersion(),dev.abros.anthub.AntHub.VERSION);
                throw new IllegalStateException("Ветка AntHub сервера не совпадает с anthubVersion проекта");
            }
            if(!policy.problem().isEmpty())com.mojang.logging.LogUtils.getLogger().warn("AntHub: {}. Проверка сборки выключена.",policy.problem());
            else if(policy.offline())com.mojang.logging.LogUtils.getLogger().warn("AntHub: GitHub недоступен; используется сохранённый снимок проекта {}, версия {}, проверенный {}.",policy.repository(),policy.version(),policy.release().checkedAt());
            else if(!policy.repository().isEmpty())com.mojang.logging.LogUtils.getLogger().info("AntHub: проект {}, версия {}; проверка сборки: {}.",policy.repository(),policy.version(),policy.required());
        } catch(Exception failure) {
            com.mojang.logging.LogUtils.getLogger().error("AntHub: не удалось подготовить проект. Запуск сервера ОСТАНОВЛЕН: {}",failure.getMessage());
            throw new IllegalStateException("AntHub: проверьте project и requireProjectPack в config/anthub-server.toml. "+failure.getMessage(),failure);
        }
    }
    public static boolean luckPermsEnabled(){return ServerDatabase.settings().flag("integrations.luckperms");}
    public static String helpText(){return ServerDatabase.settings().text("menu.helpText");}
    public static String project(){var current=policy;return current==null?"":current.repository();}
    public static String packVersion(){var current=policy;return current==null?"":current.version();}
    public static String requiredHash(){var current=policy;return current==null||!current.required()?"":current.hash();}

    private static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("ah").then(Commands.literal("project")
            .requires(source->source.hasPermission(2)||source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player&&ServerFeatures.admin(player))
            .then(Commands.literal("status").executes(context->{
                var current=policy;
                String text=current==null?"AntHub: проект ещё не загружен.":
                    "Проект: "+(current.repository().isEmpty()?"не задан":current.repository())+
                    "\nПроверка сборки: "+(current.required()?"включена":"выключена")+
                    "\nПринятая версия: "+(current.version().isEmpty()?"—":current.version())+
                    "\nПоследняя попытка проверки: "+current.checkedAt()+
                    "\nРезультат: "+(!current.problem().isEmpty()?current.problem():current.release()==null?"проект не задан":current.offline()?"сохранённый снимок от "+current.release().checkedAt():"загружено с GitHub")+
                    "\nТребования обновляются при перезапуске сервера.";
                context.getSource().sendSuccess(()->Component.literal(text),false);return 1;
            }))));
    }
    private static void tasks(RegisterConfigurationTasksEvent event){
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server!=null&&event.getListener() instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl listener&&!ServerFeatures.mayJoin(listener.getOwner(),server)){event.getListener().disconnect(ServerFeatures.maintenanceMessage());return;}
        var current=policy;if(current==null)return;
        boolean available=event.getListener().hasChannel(Protocol.Hello.TYPE)&&event.getListener().hasChannel(Protocol.ClientState.TYPE);
        if(!available){if(current.required()||AuthServer.enabled())event.getListener().disconnect(Component.literal("Для этого сервера требуется AntHub "+ConnectionCompatibility.branch(dev.abros.anthub.AntHub.VERSION)));return;}
        var pending=new Pending(UUID.randomUUID().toString(),current);var listener=event.getListener();
        event.register(new ICustomConfigurationTask(){
            public ConfigurationTask.Type type(){return TYPE;}
            public void run(Consumer<CustomPacketPayload> sender){
                NONCES.put(listener,pending);JsonObject hello=new JsonObject();hello.addProperty("coreVersion",dev.abros.anthub.AntHub.VERSION);hello.add("protocols",WireProtocols.current());hello.add("features",ConnectionCompatibility.features());hello.addProperty("protocolVersion",WireProtocols.version("pack"));hello.addProperty("nonce",pending.nonce());hello.addProperty("repository",current.repository());hello.addProperty("serverId",current.serverId());hello.addProperty("requiredVersion",current.version());hello.addProperty("requiredLockSha256",current.hash());sender.accept(new Protocol.Hello(Json.GSON.toJson(hello)));
                CompletableFuture.delayedExecutor(ServerDatabase.settings().number("connection.handshakeTimeoutSeconds"),TimeUnit.SECONDS).execute(()->{if(server!=null)server.execute(()->{if(NONCES.remove(listener,pending))listener.disconnect(Component.literal("AntHub: handshake timeout"));});});
            }
        });
    }
    private static void reply(JsonObject state,net.neoforged.neoforge.network.handling.IPayloadContext context){
        var pending=NONCES.remove(context.listener());if(pending==null||!pending.nonce().equals(Json.opt(state,"nonce",""))){context.disconnect(Component.literal("AntHub: unexpected reply"));return;}
        String compatibility=ConnectionCompatibility.failure(dev.abros.anthub.AntHub.VERSION,Json.opt(state,"coreVersion",""),state.getAsJsonObject("protocols"));
        if(!compatibility.isEmpty()){com.mojang.logging.LogUtils.getLogger().warn("AntHub: rejected incompatible client {}, server {}",Json.opt(state,"coreVersion",""),dev.abros.anthub.AntHub.VERSION);context.disconnect(Component.literal(compatibility));return;}
        var features=ConnectionCompatibility.common(state.get("features"));
        if(AuthServer.enabled()&&!features.contains("auth")||pending.policy().required()&&!features.contains("pack")){context.disconnect(Component.literal("В клиенте AntHub отсутствуют необходимые серверу функции"));return;}
        String failure=pending.policy().verify(state);if(!failure.isEmpty()){context.disconnect(Component.literal(failure));return;}
        if(context.listener() instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl listener)ServerFeatures.record(listener.getOwner().getId(),state);
        FEATURES.put(context.connection(),features);
        context.finishCurrentTask(TYPE);
    }
}
