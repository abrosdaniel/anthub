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
  int x=getX(),y=getY();float t=UiTheme.hover(this);if(selected){g.fillGradient(x,y,x+width,y+height,UiPalette.color(0xFF324B59),UiPalette.color(0xFF283E4A));g.fill(x,y,x+2,y+height,UiPalette.color(0xFF85CFBD));}else if(t>0)g.fill(x,y,x+width,y+height,UiTheme.mix(UiPalette.color(0x002B3A45),UiPalette.color(0xC032444F),t));

  var font=Minecraft.getInstance().font;g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(getMessage(),Math.max(1,width-16))),getX()+8,getY()+6,selected?UiPalette.color(0xFFD5F4EB):UiPalette.color(0xFFBAC9D3));
  if(isFocused())g.renderOutline(getX(),getY(),width,height,UiPalette.color(0xFFE2BE75));
 }
}
