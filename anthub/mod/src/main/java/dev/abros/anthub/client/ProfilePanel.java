package dev.abros.anthub.client;
import dev.abros.anthub.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.*;
final class ProfilePanel {
 private ProfilePanel(){}

 static void render(GuiGraphics g,Font font,int x,int y,int w,boolean full){
  var mc=Minecraft.getInstance();var state=ServerMenuClient.state;var meta=dev.abros.anthub.network.Protocol.profile;
  g.fill(x,y,x+w,y+(full?168:66),AccessibilityScreen.background(0xDD1C2731));g.fill(x,y,x+3,y+(full?168:66),AccessibilityScreen.background(0xFFE2BE75));
  if(full&&mc.player!=null)net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,mc.player.getSkin(),x+10,y+10,32);
  if(full)g.drawString(font,"Мой профиль",x+50,y+20,0xE2BE75);
  var display=PlayerText.text(Json.opt(meta,"prefix","")+Json.opt(state,"name","Игрок")+Json.opt(meta,"suffix",""));
  int textY=y+(full?50:7),lineY=textY;
  g.enableScissor(x+7,y+4,x+w-7,y+(full?136:42));
  for(var line:font.split(display,w-18)){if(lineY>=textY+(full?24:12))break;g.drawString(font,line,x+9,lineY,0xFFFFFF);lineY+=12;}
  if(full){String seconds=DisplayCounts.text(state,"sessionSeconds","0");if(state.has("sessionSeconds"))g.drawString(font,"В игре: "+Long.parseLong(seconds)/60+" мин.",x+9,y+96,AccessibilityScreen.foreground(0xBAC7D2));g.drawString(font,"Онлайн: "+DisplayCounts.text(state,"online","—")+" / "+DisplayCounts.text(state,"maximum","—"),x+9,y+110,AccessibilityScreen.foreground(0xBAC7D2));g.drawString(font,AuthClient.available()?"AntHub Auth · вход выполнен":"Вход через Minecraft",x+9,y+124,0x79CBA6);}
  g.disableScissor();
 }
}
