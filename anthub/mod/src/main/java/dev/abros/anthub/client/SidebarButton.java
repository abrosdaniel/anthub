package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Sidebar navigation with a readable selected state, including keyboard focus. */
final class SidebarButton extends Button {
 private final boolean selected;
 SidebarButton(int x,int y,int width,String label,boolean selected,Runnable action){super(x,y,width,20,Component.literal(label),b->action.run(),DEFAULT_NARRATION);this.selected=selected;active=!selected;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
  g.fill(getX(),getY(),getX()+width,getY()+height,selected?0xEE304052:isHoveredOrFocused()?0xDD283541:0xB0172029);
  if(selected)g.fill(getX(),getY(),getX()+3,getY()+height,0xFFE2BE75);
  var font=Minecraft.getInstance().font;g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(getMessage(),Math.max(1,width-16))),getX()+8,getY()+6,selected?0xFFE2BE75:0xFFDEE6EC);
  if(isFocused())g.renderOutline(getX(),getY(),width,height,0xFFE2BE75);
 }
}
