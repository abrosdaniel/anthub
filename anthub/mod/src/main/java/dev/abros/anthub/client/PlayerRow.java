package dev.abros.anthub.client;
import com.google.gson.JsonObject;
import dev.abros.anthub.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Compact selectable player card; standard Button keeps keyboard and narration support. */
final class PlayerRow extends Button {
 private final JsonObject player;private final boolean selected;
 PlayerRow(int x,int y,int width,JsonObject player,Runnable action,boolean selected){super(x,y,width,24,PlayerText.name(player),b->action.run(),DEFAULT_NARRATION);this.player=player;this.selected=selected;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
  var font=Minecraft.getInstance().font;boolean online=player.has("online")&&player.get("online").getAsBoolean();
  g.fill(getX(),getY(),getX()+width,getY()+height,selected?0xEE30485B:isHoveredOrFocused()?0xEE2B3946:0xD91B252E);
  g.fill(getX(),getY(),getX()+2,getY()+height,selected?0xFFE2BE75:online?0xFF79CBA6:0xFF637080);
  g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(getMessage(),Math.max(1,width-18))),getX()+7,getY()+3,0xFFFFFF);
  g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(PlayerText.text((online?"В сети":"Не в сети")+" · "+Json.opt(player,"role","Игрок")),Math.max(1,width-18))),getX()+7,getY()+14,0xAABAC8);
  if(isFocused())g.renderOutline(getX(),getY(),width,height,0xFFE2BE75);
 }
}
