package dev.abros.anthub.client;
import dev.abros.anthub.*;
import dev.abros.anthub.core.*;
import dev.abros.anthub.network.Protocol;
import net.minecraft.client.Minecraft;

import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.fml.ModList;
import java.util.concurrent.*;
public final class Client {
    public static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"AntHub IO");t.setDaemon(true);return t;});
    public static volatile java.nio.file.Path loadedJar;public static volatile Hub hub;public static volatile String error="";public static volatile String pending="";
    private static boolean initialized;
    public static Component tr(String key,Object...args){return Component.translatable("anthub."+key,args);}
    public static void install(net.neoforged.bus.api.IEventBus bus){ServerMenuClient.install(bus);loadedJar=System.getProperty("anthub.bundlePath")==null?ModList.get().getModFileById("anthub").getFile().getFilePath():java.nio.file.Path.of(System.getProperty("anthub.bundlePath"));NeoForge.EVENT_BUS.addListener(Client::screen);NeoForge.EVENT_BUS.addListener(Client::renderVersion);Protocol.clientState=()->{
        var j=new com.google.gson.JsonObject();j.addProperty("coreVersion",AntHub.VERSION);j.addProperty("packVersion",hub==null||hub.active()==null?"":hub.active().version());j.addProperty("lockSha256",hub==null?"":hub.activeHash());j.addProperty("repository",hub==null||hub.active()==null?"":hub.active().repository());try{j.addProperty("requiredFilesDigest",hub==null||hub.active()==null?"":PackProof.digest(hub.active(),hub.game));}catch(Exception ex){j.addProperty("requiredFilesDigest","invalid");}return j;
    };}
    private static void screen(ScreenEvent.Init.Post e){
        if(!(e.getScreen() instanceof TitleScreen))return;
        if(!initialized){initialized=true;IO.submit(()->{try{hub=new Hub(Minecraft.getInstance().gameDirectory.toPath(),AntHub.VERSION,ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString());Minecraft.getInstance().execute(Client::syncServers);for(String repository:hub.saved()){try{hub.details.refresh(repository);}catch(Exception failure){Client.failure(failure);}}Minecraft.getInstance().execute(Client::syncServers);IO.submit(()->{try{String id=hub.checkCoreUpdate(loadedJar);if(!id.isEmpty()){pending=id;Minecraft.getInstance().execute(()->Minecraft.getInstance().setScreen(new RestartScreen(Minecraft.getInstance().screen,id)));}}catch(Exception ex){failure(ex);}});var intent=hub.consumePendingConnection();if(intent!=null)Minecraft.getInstance().execute(()->Minecraft.getInstance().setScreen(new ConnectionCountdown(Minecraft.getInstance().screen,intent)));}catch(Exception ex){error=Errors.message(ex);}});}
        var screen=e.getScreen();
        e.addListener(new MenuButton(Math.max(6,screen.width-110),6,screen));e.addListener(new ProjectCard(Math.max(6,screen.width-162),screen));
    }
    private static void renderVersion(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) return;
        var font = Minecraft.getInstance().font;
        int[] lines = {0};
        net.neoforged.neoforge.internal.BrandingControl.forEachLine(true, false,
                (index, text) -> lines[0] = Math.max(lines[0], index + 1));
        int y = event.getScreen().height - 10 - lines[0] * (font.lineHeight + 1);
        event.getGuiGraphics().drawString(font, "AntHub " + AntHub.VERSION, 2, y, 0xFFFFFFFF);
    }
    public static void quickConnect(Screen parent){if(hub==null||hub.active()==null)return;var minecraft=Minecraft.getInstance();IO.submit(()->{try{var release=hub.installedRelease();minecraft.execute(()->{var screen=new HubScreen(parent,release);minecraft.setScreen(screen);screen.connectInstalled(release);});}catch(Exception e){failure(e);}});}
    public static void syncServers(){if(hub==null)return;try{
        var path=hub.game.resolve("anthub/server-list-ownership.json");var old=java.nio.file.Files.exists(path)?Json.read(path):new com.google.gson.JsonObject();var list=new ServerList(Minecraft.getInstance());list.load();
        for(var entry:old.entrySet()){var record=entry.getValue().getAsJsonObject();var server=list.get(Json.str(record,"address"));if(server!=null&&server.name.equals(Json.str(record,"name")))list.remove(server);}
        var next=new com.google.gson.JsonObject();var m=hub.active();if(m!=null)for(var server:java.util.List.of(hub.selectedServer(m)))if(list.get(server.address())==null){String name=hub.projectName(m);list.add(new ServerData(name,server.address(),ServerData.Type.OTHER),false);var record=new com.google.gson.JsonObject();record.addProperty("name",name);record.addProperty("address",server.address());next.add(server.id(),record);}
        list.save();Json.write(path,next);
    }catch(Exception e){failure(e);}}
    public static void failure(Exception e){error=Errors.message(e);}
}
