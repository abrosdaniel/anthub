package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
/** One checkable row for task stages, ballots and other explicit selections. */
final class UiChoiceRow extends Button {
 private final boolean checked;private final double progress;private final int accent;
 UiChoiceRow(int x,int y,int width,String label,boolean checked,double progress,int accent,Runnable change){
  super(x,y,width,UiKit.CONTROL_HEIGHT,Component.literal(label),b->change.run(),DEFAULT_NARRATION);
  this.checked=checked;this.progress=progress<0?-1:Math.min(1,progress);this.accent=accent;
  if(Minecraft.getInstance().font.width(label)>width-32)setTooltip(Tooltip.create(Component.literal(label)));
 }
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
  int accent=UiPalette.color(this.accent);int x=getX(),y=getY(),w=getWidth();boolean hover=active&&isHoveredOrFocused();
  g.fill(x,y,x+w,y+getHeight(),UiTheme.mix(UiKit.surface(),accent,checked?0.22f:hover?0.12f:0));
  if(progress>=0)g.fill(x,y,x+(int)(w*progress),y+getHeight(),UiTheme.mix(UiKit.surface(),accent,0.35f));
  UiKit.checkbox(g,x+UiKit.INSET,y+5,checked,accent);
  var font=Minecraft.getInstance().font;g.drawString(font,UiKit.fit(font,getMessage().getString(),w-32),x+26,y+6,UiKit.text(),false);
  if(hover)g.renderOutline(x,y,w,getHeight(),accent);
 }
}
