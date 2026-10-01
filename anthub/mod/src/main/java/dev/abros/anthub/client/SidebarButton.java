package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Sidebar navigation with a readable selected state, including keyboard focus. */
final class SidebarButton extends Button {
 private final boolean selected;
 SidebarButton(int x,int y,int width,String label,boolean selected,Runnable action){super(x,y,width,20,Component.literal(label),b->action.run(),DEFAULT_NARRATION);this.selected=selected;active=!selected;if(Minecraft.getInstance().font.width(label)>width-16)setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label)));}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
  int x=getX(),y=getY();float t=UiTheme.hover(this);if(selected){UiKit.surface(g,x,y,width,height,UiTheme.mix(UiKit.surface(),UiKit.accent(),0.14f));g.fill(x,y+4,x+2,y+height-4,UiKit.accent());}else if(t>0)g.fill(x,y,x+width,y+height,UiTheme.mix(UiPalette.color(0x002B3A45),UiPalette.color(0xC032444F),t));

  var font=Minecraft.getInstance().font;Ui.text(g,font,UiKit.fit(font,getMessage().getString(),Math.max(1,width-16)),getX()+8,getY()+6,selected?UiKit.text():UiKit.muted(),false);
  if(isFocused())g.renderOutline(getX(),getY(),width,height,UiPalette.color(0xFFE2BE75));
 }
}
