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
    static String result="";
    private static final KeyMapping OPEN=new KeyMapping("key.anthub.menu",GLFW.GLFW_KEY_F8,"key.categories.anthub");
    static JsonObject offer;
    static ServerData offerServer,lastServer;
    private static Object connection;
    private static long lastPopup;private static long lastRequest;private static boolean stateRequested;
    private static boolean lastServerSupported;
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
        Protocol.serverHello=hello->{offer=hello.deepCopy();offerServer=Minecraft.getInstance().getCurrentServer();};
        try{var p=Minecraft.getInstance().gameDirectory.toPath().resolve("anthub/menu-settings.json");if(java.nio.file.Files.exists(p)){var j=Json.read(p);notices=j.get("notices").getAsBoolean();sound=j.get("sound").getAsBoolean();restartNotices=!j.has("restartNotices")||j.get("restartNotices").getAsBoolean();}}catch(Exception ignored){}
    }
    static boolean available(){var c=Minecraft.getInstance().getConnection();return !Minecraft.getInstance().hasSingleplayerServer()&&c!=null&&c.hasChannel(Protocol.FeatureRequest.TYPE);}
    static void request(JsonObject j){if(available())PacketDistributor.sendToServer(new Protocol.FeatureRequest(Json.GSON.toJson(j)));}
    static void request(String action){var j=new JsonObject();j.addProperty("action",action);
        if(action.equals("state")){var info=new JsonObject();info.addProperty("coreVersion",dev.abros.anthub.AntHub.VERSION);info.addProperty("packVersion",Client.hub==null||Client.hub.active()==null?"":Client.hub.active().version());info.addProperty("repository",Client.hub==null||Client.hub.active()==null?"":Client.hub.active().repository());info.addProperty("lockSha256",Client.hub==null?"":Client.hub.activeHash());j.add("client",info);}
        request(j);
    }
    static void open(){if(!available())return;request("state");Minecraft.getInstance().setScreen(new CommunityScreen(null,"home",""));}
    private static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();Object current=mc.getConnection();
        if(current!=connection){connection=current;state=new JsonObject();result="";Protocol.profile=new JsonObject();lastRequest=0;stateRequested=false;lastPopup=0;CommunityScreen.clearDrafts();}
        if(current!=null){lastServerSupported=available();lastServer=mc.getCurrentServer();offer=null;offerServer=null;}
        if(!available()&&(mc.screen instanceof ServerMenuScreen||mc.screen instanceof ReportScreen))mc.setScreen(null);
        while(OPEN.consumeClick())if(mc.player!=null&&mc.screen==null)open();
        if(available()&&System.currentTimeMillis()-lastRequest>5000){lastRequest=System.currentTimeMillis();if(!stateRequested){stateRequested=true;request("state");}}
    }
    private static void receive(JsonObject j){
        var mc=Minecraft.getInstance();if(!available())return;String kind=Json.opt(j,"kind","");
        if(kind.equals("community")){if(mc.screen instanceof CommunityScreen screen)screen.receive(j);else if(mc.screen instanceof CommunityScreen.Receiver receiver)receiver.receiveCommunity(j);return;}
        if(java.util.Set.of("reports","myReports","players","history","menuData").contains(kind)){if(mc.screen instanceof FeatureListScreen list&&(list.kind.equals(kind)||kind.equals("menuData")&&list.kind.equals("links")))list.receive(j);return;}
        if(kind.equals("state")){boolean wasAdmin=admin();state=j;long sequence=j.has("popupSequence")?j.get("popupSequence").getAsLong():0;if(sequence>lastPopup){lastPopup=sequence;if(notices){NoticeToast.show(Client.tr("server.notice"),Component.literal("Новые уведомления: "+j.get("unread").getAsInt()));if(sound&&mc.player!=null)mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,0.3f,1);}}if(wasAdmin!=admin()){if(mc.screen instanceof ServerMenuScreen menu)menu.refreshPermissions();else if(mc.screen instanceof CommunityScreen menu)menu.refreshPermissions();}if(j.has("profile"))Protocol.profile=j.getAsJsonObject("profile");if(j.get("open").getAsBoolean())mc.setScreen(new CommunityScreen(null,"home",""));}
        else if(kind.equals("notice")){if(!j.has("optional")||!j.get("optional").getAsBoolean()||notices){NoticeToast.show(Client.tr("server.notice"),Component.literal(Json.str(j,"text")));if(sound&&mc.player!=null)mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,0.3f,1);}}
        else if(kind.equals("diagnostics")){StringBuilder text=new StringBuilder();for(var entry:j.getAsJsonArray("players")){var p=entry.getAsJsonObject();text.append(Json.str(p,"player")).append(" · AntHub ").append(Json.opt(p,"coreVersion","—")).append(" · ").append(p.get("matching").getAsBoolean()?"✓":"≠").append("\n").append(Json.opt(p,"repository","")).append("\n");}mc.setScreen(new TextScreen(mc.screen,Client.tr("server.diagnostics"),text.toString()));}
        else {result=Json.opt(j,"text","");if(mc.screen instanceof FeatureListScreen list)list.failure(result);}
    }
    private static void screen(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post e){
        if(e.getScreen() instanceof TitleScreen||e.getScreen() instanceof ConnectScreen){lastServer=null;lastServerSupported=false;offer=null;offerServer=null;}
        if(!(e.getScreen() instanceof DisconnectedScreen))return;
        var mc=Minecraft.getInstance();var parent=e.getScreen();
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
    static boolean admin(){return state.has("admin")&&state.get("admin").getAsBoolean();}
    private static void saveSettings(){try{Json.write(Minecraft.getInstance().gameDirectory.toPath().resolve("anthub/menu-settings.json"),java.util.Map.of("notices",notices,"sound",sound,"restartNotices",restartNotices));}catch(Exception e){result=Errors.message(e);}}
    static String toggle(int setting){if(setting==1)sound=!sound;else if(setting==2)restartNotices=!restartNotices;else notices=!notices;saveSettings();return Client.tr(enabled(setting)?"server.on":"server.off").getString();}
    static boolean enabled(int setting){return setting==1?sound:setting==2?restartNotices:notices;}
}
