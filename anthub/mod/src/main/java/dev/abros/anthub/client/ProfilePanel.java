package dev.abros.anthub.client;
import dev.abros.anthub.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.*;
final class ProfilePanel {
 private ProfilePanel(){}

 static void render(GuiGraphics g,Font font,int x,int y,int w,boolean full,com.google.gson.JsonObject data){
  var mc=Minecraft.getInstance();var state=ServerMenuClient.state;var meta=dev.abros.anthub.network.Protocol.profile;
  g.fill(x,y,x+w,y+(full?(AuthClient.available()?178:152):66),AccessibilityScreen.background(UiPalette.color(0xDD1C2731)));g.fill(x,y,x+3,y+(full?(AuthClient.available()?178:152):66),AccessibilityScreen.background(UiPalette.color(0xFFE2BE75)));
  if(full&&mc.player!=null)net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,mc.player.getSkin(),x+10,y+10,32);
  if(full)g.drawString(font,"Мой профиль",x+(mc.player==null?10:50),y+20,UiPalette.color(0xE2BE75));
  var display=PlayerText.text(Json.opt(meta,"prefix","")+Json.opt(state,"name","Игрок")+Json.opt(meta,"suffix",""));
  int textY=y+(full?50:7),lineY=textY;
  g.enableScissor(x+7,y+4,x+w-7,y+(full?136:42));
  for(var line:font.split(display,w-18)){if(lineY>=textY+(full?24:12))break;g.drawString(font,line,x+9,lineY,0xFFFFFF);lineY+=12;}
  if(full){int tw=(w-26)/2;String total="—";if(data.has("selfStatistics")&&data.getAsJsonObject("selfStatistics").has("totalMillis")){long minutes=data.getAsJsonObject("selfStatistics").get("totalMillis").getAsLong()/60000;total=minutes/60+" ч "+minutes%60+" м";}tile(g,font,x+9,y+76,tw,"Время игры",total,UiPalette.color(0xD8E8F0));tile(g,font,x+17+tw,y+76,tw,"Онлайн сервера",DisplayCounts.text(state,"online","—")+" / "+DisplayCounts.text(state,"maximum","—"),UiPalette.color(0x85CFBD));g.drawString(font,AuthClient.available()?"AntHub Auth · вход выполнен":"Вход через Minecraft",x+9,y+114,UiPalette.color(0x79CBA6),false);}

  g.disableScissor();
 }
 private static void tile(GuiGraphics g,Font font,int x,int y,int w,String label,String value,int color){g.fill(x,y,x+w,y+30,UiPalette.color(0xFF15232D));g.drawString(font,font.plainSubstrByWidth(label,w-8),x+4,y+4,UiPalette.color(0x99ADB9),false);g.drawString(font,font.plainSubstrByWidth(value,w-8),x+4,y+17,color,false);}
}
