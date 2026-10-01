package dev.abros.anthub.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
/** Theme-aware input retaining native editing, selection and mouse hit testing. */
final class UiEditBox extends EditBox {
 UiEditBox(Font font,int x,int y,int w,int h,Component label){super(font,x,y,w,h,label);}
 @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta){
  if(!isVisible())return;
  boolean border=isBordered();int x=getX(),y=getY(),w=getWidth(),h=getHeight();
  setTextColor(UiKit.text());setTextColorUneditable(UiKit.muted());setTextShadow(!UiPalette.light());
  if(!border){super.renderWidget(g,mx,my,delta);return;}
  UiKit.surface(g,x,y,w,h,UiPalette.inputSurface());
  g.renderOutline(x,y,w,h,isFocused()?UiKit.accent():UiPalette.color(0xFF536672));
  // Native bordered geometry is reproduced while replacing only the background sprite.
  setBordered(false);setX(x+4);setY(y+(h-8)/2);setWidth(Math.max(1,w-8));
  try{super.renderWidget(g,mx,my,delta);}finally{setX(x);setY(y);setWidth(w);setBordered(true);}
 }
}
