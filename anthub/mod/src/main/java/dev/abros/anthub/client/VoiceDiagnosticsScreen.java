package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Optional read-only API inspection. Unknown fields remain unknown, never guessed from TCP. */
final class VoiceDiagnosticsScreen extends ScrollScreen {
 private final Screen parent;private List<String> lines=List.of();private long checked;
 VoiceDiagnosticsScreen(Screen parent){super(Component.literal("Голосовой чат"));this.parent=parent;}
 @Override protected void init(){scrollArea(lines.size(),44,height-48,24,width/2+154);addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width/2-100,height-28,200,20).build());}
 private static Object call(Object instance,String api,String method)throws ReflectiveOperationException{return Class.forName(api).getMethod(method).invoke(instance);}
 @Override public void tick(){if(System.currentTimeMillis()-checked<1000)return;checked=System.currentTimeMillis();boolean installed=net.neoforged.fml.ModList.get().isLoaded("plasmovoice");String connection="неизвестно",microphone="неизвестно",muted="неизвестно",device="неизвестно";
  if(installed)try{
   Object client=Class.forName("su.plo.voice.client.ModVoiceClient").getField("INSTANCE").get(null);String api="su.plo.voice.api.client.PlasmoVoiceClient";
   if(client!=null){Object udp=call(client,api,"getUdpClientManager");connection=Boolean.TRUE.equals(call(udp,"su.plo.voice.api.client.connection.UdpClientManager","isConnected"))?"подключён":"нет соединения";
    Object manager=call(client,api,"getDeviceManager");device=((Optional<?>)call(manager,"su.plo.voice.api.client.audio.device.DeviceManager","getInputDevice")).isPresent()?"доступно":"не открыто";
    var server=(Optional<?>)call(client,api,"getServerConnection");if(server.isPresent()&&minecraft.player!=null){var player=(Optional<?>)Class.forName("su.plo.voice.api.client.connection.ServerConnection").getMethod("getPlayerById",UUID.class).invoke(server.get(),minecraft.player.getUUID());if(player.isPresent()){String type="su.plo.voice.proto.data.player.VoicePlayerInfo";muted=Boolean.TRUE.equals(call(player.get(),type,"isMuted"))?"да":"нет";microphone=Boolean.TRUE.equals(call(player.get(),type,"isMicrophoneMuted"))?"выключен":"включён";}}
   }
  }catch(ReflectiveOperationException|LinkageError|RuntimeException unsupported){/* Values already obtained remain valid; unavailable API fields stay unknown. */}
  lines=List.of("Plasmo Voice: "+(installed?"установлен":"не установлен"),"UDP: "+connection,"Устройство записи: "+device,"Микрофон: "+microphone,"Серверный mute: "+muted,"Настройки Plasmo Voice: клавиша V");rebuildWidgets();
 }
 @Override public void render(GuiGraphics g,int x,int y,float delta){super.render(g,x,y,delta);g.drawCenteredString(font,title,width/2,16,0xE2BE75);int left=Math.max(12,width/2-150);for(int i=firstRow;i<Math.min(lines.size(),firstRow+visibleRows);i++)g.drawString(font,font.plainSubstrByWidth(lines.get(i),width-left-12),left,44+(i-firstRow)*24,0xEEEEEE);}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
