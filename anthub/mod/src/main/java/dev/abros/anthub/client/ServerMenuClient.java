package dev.abros.anthub.client;

import com.google.gson.*;
import dev.abros.anthub.core.*;
import dev.abros.anthub.network.Protocol;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ServerMenuClient {
    static JsonObject state=new JsonObject();
    private static String requiredBranch="";
    static java.util.function.Consumer<JsonObject> previewTransport;
    static String result="";
    private static final KeyMapping OPEN=new KeyMapping("key.anthub.menu",GLFW.GLFW_KEY_F8,"key.categories.anthub");
    static JsonObject offer;
    static ServerData offerServer,lastServer;
    private static Object connection;
    private static long lastPopup;private static long lastRequest;private static boolean stateRequested;
    private record Pending(JsonObject packet,Screen owner,boolean read){}
    private static boolean screenRead(JsonObject packet){String action=Json.opt(packet,"action","");return action.equals("moderationVote")&&Json.opt(packet,"op","").equals("view")||action.equals("community")&&java.util.Set.of("list","detail").contains(Json.opt(packet,"op",""))||java.util.Set.of("players","reports","myReports","myReport","history","menuData","playerAdministration").contains(action);}
    private static final java.util.ArrayDeque<Pending> outbound=new java.util.ArrayDeque<>();
    private static final java.util.Map<String,Long> dispatched=new java.util.HashMap<>();
    private static boolean lastServerSupported;private static String subscription="";private static long subscriptionAt;
    private static boolean notices=true,sound=true,restartNotices=true;
    public static void install(IEventBus bus){
        AuthClient.install();
        bus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e)->e.register(OPEN));
        NeoForge.EVENT_BUS.addListener(ServerMenuClient::tick);
        NeoForge.EVENT_BUS.addListener(ServerMenuClient::screen);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Post e)->{
            if(!restartNotices||!available()||Minecraft.getInstance().screen!=null||!state.has("restartAt"))return;
            long end=state.get("restartAt").getAsLong();if(end<=0)return;var mc=Minecraft.getInstance();String text=Client.tr("server.countdown",Math.max(0,(end-System.currentTimeMillis()+999)/1000)).getString();
            int width=mc.font.width(text);e.getGuiGraphics().fill(e.getGuiGraphics().guiWidth()-width-14,6,e.getGuiGraphics().guiWidth()-6,24,0xBB202020);e.getGuiGraphics().drawString(mc.font,text,e.getGuiGraphics().guiWidth()-width-10,11,0xFFE2BE75);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre e)->{if(e.getButton()==0&&NoticeToast.dismiss(e.getMouseX(),e.getMouseY()))e.setCanceled(true);});
        Protocol.featureState=ServerMenuClient::receive;
        Protocol.incompatible=branch->requiredBranch=branch;
        Protocol.serverHello=hello->{if(!Json.opt(hello,"repository","").isEmpty()){offer=hello.deepCopy();offerServer=Minecraft.getInstance().getCurrentServer();}};
        try{var p=Minecraft.getInstance().gameDirectory.toPath().resolve("anthub/menu-settings.json");if(java.nio.file.Files.exists(p)){var j=Json.read(p);notices=j.get("notices").getAsBoolean();sound=j.get("sound").getAsBoolean();restartNotices=!j.has("restartNotices")||j.get("restartNotices").getAsBoolean();}}catch(Exception ignored){}
    }
    static boolean available(){if(previewTransport!=null)return true;var c=Minecraft.getInstance().getConnection();return !Minecraft.getInstance().hasSingleplayerServer()&&c!=null&&Protocol.supportedFeatures.contains("menu")&&c.hasChannel(Protocol.FeatureRequest.TYPE);}
    static void request(JsonObject j){if(previewTransport!=null){previewTransport.accept(j.deepCopy());return;}if((Json.opt(j,"action","").equals("community")&&!java.util.Set.of("list","detail").contains(Json.opt(j,"op",""))||java.util.Set.of("report","reply","moderate").contains(Json.opt(j,"action","")))&&!j.has("operationId")){j.addProperty("operationId",java.util.UUID.randomUUID().toString());j.addProperty("issuedAt",System.currentTimeMillis());}if(available()){boolean read=screenRead(j);Screen owner=requestOwner(Minecraft.getInstance().screen);
        // Only replace the same read; pages and mutations retain their order and identity.
        if(read)outbound.removeIf(p->p.read&&p.owner==owner&&sameRead(p.packet,j));
        if(Json.opt(j,"action","").equals("subscribe"))outbound.removeIf(p->Json.opt(p.packet,"action","").equals("subscribe"));
        if(outbound.size()>=64){result="Слишком много запросов. Подождите завершения текущих.";return;}outbound.add(new Pending(j.deepCopy(),owner,read));}}
    private static Screen requestOwner(Screen screen){while(screen instanceof ChoicePopup popup)screen=popup.parentScreen();return screen;}
    private static boolean sameRead(JsonObject first,JsonObject second){for(String key:java.util.List.of("action","op","section","id","cursor","query","mine","participating","member","archive","trash"))if(!java.util.Objects.equals(first.get(key),second.get(key)))return false;return true;}
    static void request(String action){var j=new JsonObject();j.addProperty("action",action);
        if(action.equals("state")){j.addProperty("menuProtocol",MenuProtocol.VERSION);var info=new JsonObject();info.addProperty("coreVersion",dev.abros.anthub.AntHub.VERSION);info.addProperty("packVersion",Client.hub==null||Client.hub.active()==null?"":Client.hub.active().version());info.addProperty("repository",Client.hub==null||Client.hub.active()==null?"":Client.hub.active().repository());info.addProperty("lockSha256",Client.hub==null?"":Client.hub.activeHash());j.add("client",info);}
        request(j);
    }
    static void open(){if(!available())return;request("state");Minecraft.getInstance().setScreen(new CommunityScreen(null,"home",""));}
    private static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){XaeroMapBridge.tick();
        var mc=Minecraft.getInstance();if(previewTransport!=null)return;Object current=mc.getConnection();
        if(current!=connection){connection=current;state=new JsonObject();result="";Protocol.profile=new JsonObject();lastRequest=0;stateRequested=false;lastPopup=0;CommunityScreen.clearDrafts();subscription="";subscriptionAt=0;outbound.clear();dispatched.clear();}
        if(available()){long now=System.currentTimeMillis();var pending=outbound.iterator();while(pending.hasNext()){var queued=pending.next();if(queued.read&&queued.owner!=requestOwner(mc.screen)){pending.remove();continue;}var packet=queued.packet;String action=Json.opt(packet,"action","");if(now-dispatched.getOrDefault(action,0L)<550)continue;pending.remove();dispatched.put(action,now);PacketDistributor.sendToServer(new Protocol.FeatureRequest(Json.GSON.toJson(packet)));}}
        if(available()){
            String topic=requestOwner(mc.screen) instanceof CommunityScreen menu?menu.section+"|"+menu.itemId:mc.screen instanceof NotificationPopup?"notifications|":mc.screen instanceof FeatureListScreen list?list.kind+"|":"|";
            long now=System.currentTimeMillis();if(!topic.equals(subscription)||now-subscriptionAt>10000){subscription=topic;subscriptionAt=now;var parts=topic.split("\\|",-1);var sub=new JsonObject();sub.addProperty("action","subscribe");sub.addProperty("section",parts[0]);sub.addProperty("id",parts[1]);request(sub);}
        }
        if(current!=null){lastServerSupported=available();lastServer=mc.getCurrentServer();offer=null;offerServer=null;}
        if(!available()&&(mc.screen instanceof ServerMenuScreen||mc.screen instanceof ReportScreen))mc.setScreen(null);
        while(OPEN.consumeClick())if(mc.player!=null&&mc.screen==null)open();
        if(available()&&System.currentTimeMillis()-lastRequest>5000){lastRequest=System.currentTimeMillis();if(!stateRequested){stateRequested=true;request("state");}}
    }
    static void receive(JsonObject j){
        var mc=Minecraft.getInstance();if(!available())return;String kind=Json.opt(j,"kind","");
        if(kind.equals("incompatible")||kind.equals("state")&&(!j.has("menuProtocol")||j.get("menuProtocol").getAsInt()!=MenuProtocol.VERSION)){result=kind.equals("incompatible")?Json.opt(j,"text","Меню этого сервера временно недоступно для вашей версии AntHub."):"Меню этого сервера временно недоступно для вашей версии AntHub.";if(mc.screen instanceof CommunityScreen||mc.screen instanceof FeatureListScreen)mc.setScreen(new TextScreen(null,Component.literal("Обновление AntHub"),result));return;}
        if(kind.equals("moderationVote")){if(mc.screen instanceof ModerationVoteScreen screen)screen.receiveCommunity(j);return;}
        if(kind.equals("playerAdministration")){if(mc.screen instanceof PlayerAdministrationScreen screen)screen.receive(j);return;}
        if(kind.equals("changed")){if(mc.screen instanceof CommunityScreen screen)screen.invalidate();else if(mc.screen instanceof FeatureListScreen screen)screen.invalidate();else if(mc.screen instanceof NotificationPopup screen)screen.invalidate();else if(mc.screen instanceof ChoicePopup popup)popup.invalidate();return;}
        if(kind.equals("community")){if(mc.screen instanceof CommunityScreen screen)screen.receive(j);else if(mc.screen instanceof CommunityScreen.Receiver receiver)receiver.receiveCommunity(j);return;}
        if(java.util.Set.of("reports","myReports","players","history","menuData").contains(kind)){if(mc.screen instanceof FeatureListScreen list&&(list.kind.equals(kind)||kind.equals("menuData")&&list.kind.equals("links")))list.receive(j);return;}
        if(kind.equals("state")){String previousPermissions=permissions(state);long previousSequence=state.has("popupSequence")?state.get("popupSequence").getAsLong():0;int previousUnread=state.has("unread")?state.get("unread").getAsInt():0;state=j;long sequence=j.has("popupSequence")?j.get("popupSequence").getAsLong():0;if(mc.screen instanceof NotificationPopup popup&&(sequence!=previousSequence||j.get("unread").getAsInt()!=previousUnread))popup.invalidate();if(sequence>lastPopup){lastPopup=sequence;if(notices){NoticeToast.show(Client.tr("server.notice"),Component.literal("Новые уведомления: "+j.get("unread").getAsInt()));if(sound&&mc.player!=null)mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,0.3f,1);}}if(!previousPermissions.equals(permissions(state))){if(mc.screen instanceof ServerMenuScreen menu)menu.refreshPermissions();else if(mc.screen instanceof CommunityScreen menu)menu.refreshPermissions();}if(j.has("profile"))Protocol.profile=j.getAsJsonObject("profile");if(j.get("open").getAsBoolean())mc.setScreen(new CommunityScreen(null,"home",""));}
        else if(kind.equals("notice")){if(!j.has("optional")||!j.get("optional").getAsBoolean()||notices){NoticeToast.show(Client.tr("server.notice"),Component.literal(Json.str(j,"text")));if(sound&&mc.player!=null)mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,0.3f,1);}}
        else if(kind.equals("diagnostics")){StringBuilder text=new StringBuilder();if(j.has("version")){text.append("AntHub ").append(Json.str(j,"version")).append("\nPostgreSQL: ").append(Json.opt(j,"database","—")).append(" · ").append((j.has("databaseMillis")?j.get("databaseMillis").getAsString():"—")).append(" мс\nLuckPerms: ").append(j.get("luckPerms").getAsBoolean()?"включён":"выключен").append("\nPlasmo Voice: ").append(j.get("plasmoVoice").getAsBoolean()?"установлен":"не установлен").append("\n\n");for(var error:j.getAsJsonArray("recentErrors")){var row=error.getAsJsonObject();text.append(Json.str(row,"id")).append(" · ").append(Json.str(row,"operation")).append(" · ").append(Json.str(row,"type")).append(" × ").append(row.get("count")).append("\n");}text.append("\nКлиенты\n");}for(var entry:j.getAsJsonArray("players")){var p=entry.getAsJsonObject();text.append(Json.str(p,"player")).append(" · AntHub ").append(Json.opt(p,"coreVersion","—")).append(" · ").append(p.get("matching").getAsBoolean()?"✓":"≠").append("\n").append(Json.opt(p,"repository","")).append("\n");}mc.setScreen(new TextScreen(mc.screen,Client.tr("server.diagnostics"),text.toString()));}
        else {if(mc.screen instanceof CommunityScreen.Receiver receiver)receiver.receiveCommunity(j);result=Json.opt(j,"text","");if(mc.screen instanceof FeatureListScreen list)list.failure(result);}
    }
    private static void screen(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post e){
        if(e.getScreen() instanceof TitleScreen||e.getScreen() instanceof ConnectScreen){requiredBranch="";Protocol.supportedFeatures=java.util.Set.of();lastServer=null;lastServerSupported=false;offer=null;offerServer=null;}
        if(!(e.getScreen() instanceof DisconnectedScreen))return;
        var mc=Minecraft.getInstance();var parent=e.getScreen();
        if(!requiredBranch.isEmpty()){String branch=requiredBranch;e.addListener(Button.builder(Component.literal("Выбрать версию AntHub"),b->mc.setScreen(new CoreVersionsPopup(parent,branch))).bounds(parent.width/2-100,parent.height-30,200,20).build());return;}
        if(offer!=null&&offerServer!=null&&Client.hub!=null){var captured=offer.deepCopy();var server=offerServer;offer=null;offerServer=null;
            e.addListener(Button.builder(Client.tr("server.update"),b->mc.setScreen(new ServerUpdateScreen(parent,captured,server))).bounds(parent.width/2-100,parent.height-54,200,20).build());
        }else if(lastServer!=null&&lastServerSupported){var server=lastServer;e.addListener(Button.builder(Client.tr("server.reconnect"),b->mc.setScreen(new ConnectionCountdown(parent,new Manifest.Server("main",server.name,server.ip)))).bounds(parent.width/2-100,parent.height-54,200,20).build());}
    }
    static String header(){
        String text=Minecraft.getInstance().getCurrentServer()==null?"AntHub":Minecraft.getInstance().getCurrentServer().name;
        if(!available())return text+" · "+Client.tr("server.unavailable").getString();
        if(state.has("maintenance")&&state.get("maintenance").getAsBoolean())text+=" · "+Client.tr("server.maintenance").getString();
        long restart=state.has("restartAt")?state.get("restartAt").getAsLong():0;
        if(restart>0)text+=" · "+Client.tr("server.countdown",Math.max(0,(restart-System.currentTimeMillis()+999)/1000)).getString();return text;
    }
    private static String permissions(JsonObject value){var snapshot=new JsonObject();for(String key:java.util.List.of("admin","authReset","actions","capabilities","communityConfig"))if(value.has(key))snapshot.add(key,value.get(key));if(value.has("profile")){var profile=value.getAsJsonObject("profile");if(profile.has("capabilities"))snapshot.add("profileCapabilities",profile.get("capabilities"));}return snapshot.toString();}
    static boolean supports(String feature){return state.has("features")&&state.getAsJsonArray("features").contains(new JsonPrimitive(feature));}
    static boolean admin(){return state.has("admin")&&state.get("admin").getAsBoolean();}
    private static void saveSettings(){try{Json.write(Minecraft.getInstance().gameDirectory.toPath().resolve("anthub/menu-settings.json"),java.util.Map.of("notices",notices,"sound",sound,"restartNotices",restartNotices));}catch(Exception e){result=Errors.message(e);}}
    static String toggle(int setting){if(setting==1)sound=!sound;else if(setting==2)restartNotices=!restartNotices;else notices=!notices;saveSettings();return Client.tr(enabled(setting)?"server.on":"server.off").getString();}
    static boolean enabled(int setting){return setting==1?sound:setting==2?restartNotices:notices;}
}
