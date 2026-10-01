package dev.abros.anthub.client;
import com.google.gson.JsonObject;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
/** A shared thumbnail component for both the library and the quick selector. */
final class UiSkinCard extends Button {
 private final JsonObject entry;private final boolean selected,applied,grid;
 UiSkinCard(int x,int y,int w,int h,JsonObject entry,boolean selected,boolean applied,boolean grid,Runnable action){
  super(x,y,w,h,Component.literal(entry==null?"Обычный скин":Json.str(entry,"name")),b->action.run(),DEFAULT_NARRATION);
  this.entry=entry;this.selected=selected;this.applied=applied;this.grid=grid;
  setTooltip(Tooltip.create(Component.literal(getMessage().getString()+(applied?" · активен":""))));
 }
 static PlayerSkin appearance(JsonObject entry){var skin=SkinClient.ordinarySkin();if(entry!=null){var texture=SkinClient.texture(Json.str(entry,"hash"));if(texture!=null)skin=new PlayerSkin(texture,null,null,null,entry.get("slim").getAsBoolean()?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE,false);}return skin;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float d){
  int x=getX(),y=getY(),w=getWidth(),h=getHeight();float hover=UiTheme.hover(this);
  UiKit.plate(g,x,y,w,h,UiTheme.mix(UiKit.surface(),UiPalette.color(0xFF344657),hover));
  int border=selected?UiPalette.color(0xFFE2BE75):UiPalette.color(0xFF526674);
  
  if(selected)g.fill(x,y+5,x+2,y+h-5,border);if(isFocused())g.renderOutline(x,y,w,h,border);
  var font=net.minecraft.client.Minecraft.getInstance().font;
  if(grid){int size=Math.min(28,h-24);PlayerFaceRenderer.draw(g,appearance(entry),x+(w-size)/2,y+6,size);Ui.centered(g,font,UiKit.fit(font,getMessage().getString(),w-12),x+w/2,y+h-13,UiKit.text());}
  else{int size=Math.min(24,h-12);PlayerFaceRenderer.draw(g,appearance(entry),x+8,y+(h-size)/2,size);Ui.text(g,font,UiKit.fit(font,getMessage().getString(),w-size-42),x+size+16,y+(h-8)/2,UiKit.text(),false);}
  if(applied)UiKit.checkbox(g,x+w-16,y+6,true,UiKit.accent());
 }
}
