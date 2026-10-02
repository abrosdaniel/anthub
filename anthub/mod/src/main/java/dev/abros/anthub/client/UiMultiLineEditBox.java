package dev.abros.anthub.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
/** Theme-aware scroll decorations for AntHub fields only; editing and input remain vanilla. */
final class UiMultiLineEditBox extends MultiLineEditBox {
 private final Font font;private final Component placeholder;private int limit=-1;private long focusAt;
 UiMultiLineEditBox(Font font,int x,int y,int w,int h,Component placeholder,Component title){super(font,x,y,w,h,placeholder,title);this.font=font;this.placeholder=placeholder;}
 @Override public void setCharacterLimit(int value){super.setCharacterLimit(value);limit=value;}
 @Override protected void renderBackground(GuiGraphics g){UiKit.surface(g,getX(),getY(),width,height,UiPalette.inputSurface());g.renderOutline(getX(),getY(),width,height,UiFields.outline(this));}
 @Override public void setFocused(boolean focused){super.setFocused(focused);if(focused)focusAt=net.minecraft.Util.getMillis();}
 @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){
  var field=((dev.abros.anthub.mixin.MultiLineFieldAccessor)(Object)this).anthub$textField();String value=field.value();int left=getX()+innerPadding(),y=getY()+innerPadding();
  if(value.isEmpty()&&!isFocused()){Ui.wrap(g,font,placeholder,left,y,width-totalInnerPadding(),UiKit.muted());return;}
  boolean caret=isFocused()&&(net.minecraft.Util.getMillis()-focusAt)/300%2==0;int cursor=field.cursor();
  for(var line:field.iterateLines()){
   if(withinContentAreaTopBottom(y,y+9)){String text=value.substring(line.beginIndex(),line.endIndex());Ui.text(g,font,text,left,y,active?UiKit.text():UiKit.muted(),false);
    if(caret&&cursor>=line.beginIndex()&&cursor<=line.endIndex()){int xx=left+font.width(value.substring(line.beginIndex(),cursor));g.fill(xx,y-1,xx+1,y+10,UiKit.accent());}
    if(field.hasSelection()){var selection=field.getSelected();int start=Math.max(line.beginIndex(),selection.beginIndex()),end=Math.min(line.endIndex(),selection.endIndex());if(end>start)g.fill(net.minecraft.client.renderer.RenderType.guiTextHighlight(),left+font.width(value.substring(line.beginIndex(),start)),y,left+font.width(value.substring(line.beginIndex(),end)),y+9,0xFF0000FF);}
   }y+=9;
  }
 }
 @Override protected void renderDecorations(GuiGraphics g){
  if(scrollbarVisible()){int h=Math.min(height,Math.max(32,height*height/Math.max(1,getInnerHeight()+4)));int y=getY()+(int)(scrollAmount()*(height-h)/Math.max(1,getMaxScrollAmount()));UiScrollbar.draw(g,new dev.abros.anthub.core.NativeLayout.Box(getX()+width,getY(),8,height),new dev.abros.anthub.core.NativeLayout.Box(getX()+width,y,8,h));}
  if(limit>=0){String text=getValue().length()+"/"+limit;Ui.text(g,font,text,getX()+width-font.width(text),getY()+height+4,UiKit.muted(),false);}
 }
}
