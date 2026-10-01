package dev.abros.anthub.client;
import dev.abros.anthub.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
final class ProfilePanel {
 private ProfilePanel(){}
 static void render(GuiGraphics g,Font font,int x,int y,int w,boolean full,com.google.gson.JsonObject data){
  var mc=Minecraft.getInstance();var state=ServerMenuClient.state;
  int h=full?(AuthClient.available()?160:134):66;UiKit.material(g,x,y,w,h);
  if(full){
   if(mc.player!=null)net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,mc.player.getSkin(),x+10,y+12,30);
   Ui.text(g,font,UiKit.fit(font,Json.opt(state,"name","Игрок"),w-62),x+50,y+14,UiKit.text(),false);
   Ui.text(g,font,"В сети",x+50,y+29,UiPalette.color(0x79CBA6),false);
   g.fill(x+10,y+50,x+w-10,y+51,UiPalette.color(0xFF46535E));
   String total="—";if(data.has("selfStatistics")&&data.getAsJsonObject("selfStatistics").has("totalMillis")){long minutes=data.getAsJsonObject("selfStatistics").get("totalMillis").getAsLong()/60000;total=minutes/60+" ч "+minutes%60+" м";}
   metric(g,font,x+10,y+61,w-20,"Время игры",total);
   metric(g,font,x+10,y+79,w-20,"Онлайн",DisplayCounts.text(state,"online","—")+" / "+DisplayCounts.text(state,"maximum","—"));
  }else{Ui.text(g,font,UiKit.fit(font,Json.opt(state,"name","Игрок"),w-20),x+10,y+12,UiKit.text(),false);}
 }
 private static void metric(GuiGraphics g,Font font,int x,int y,int w,String label,String value){Ui.text(g,font,label,x,y,UiKit.muted(),false);Ui.text(g,font,UiKit.fit(font,value,w/2),x+w-font.width(UiKit.fit(font,value,w/2)),y,UiKit.text(),false);}
}
