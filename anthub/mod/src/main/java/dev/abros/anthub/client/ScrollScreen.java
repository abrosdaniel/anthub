package dev.abros.anthub.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
/** Row scrolling retains fractional trackpad deltas, with a draggable scrollbar. */
abstract class ScrollScreen extends Screen {
 protected int firstRow,visibleRows=1;private int count,top,bottom,right;private double position;private boolean dragging;private double dragOffset;private dev.abros.anthub.core.ScrollLayout layout;
 protected ScrollScreen(Component title){super(title);}
 @Override protected void clearWidgets(){layout=null;super.clearWidgets();}

 protected dev.abros.anthub.core.ScrollLayout scrollArea(int count,dev.abros.anthub.core.NativeLayout.Box viewport,int rowHeight){
  if(rowHeight<=0||count<0)throw new IllegalArgumentException("Invalid list dimensions");
  layout=dev.abros.anthub.core.ScrollLayout.fit(viewport);this.count=count;top=viewport.y();bottom=viewport.bottom();right=layout.track().x();
  visibleRows=Math.max(0,viewport.height()/rowHeight);position=Math.max(0,Math.min(position,Math.max(0,count-visibleRows)));firstRow=(int)position;return layout;
 }
 final dev.abros.anthub.core.ScrollLayout scrollLayout(){return layout;}
 /** The list owns its trailing gutter, including fields and compound row widgets. */
 @Override protected <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> T addRenderableWidget(T child){
  if(layout!=null && child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.getY()>=top && widget.getY()+widget.getHeight()<=bottom && widget.getX()>=layout.content().x() && widget.getX()<layout.content().right() && widget.getX()+widget.getWidth()>layout.content().right())widget.setWidth(layout.content().right()-widget.getX());
  return super.addRenderableWidget(child);
 }
 private void move(double value){position=Math.max(0,Math.min(value,Math.max(0,count-visibleRows)));int next=(int)position;if(next!=firstRow){firstRow=next;rowsChanged();}if(position>=Math.max(0,count-visibleRows))onScrollEnd();}
 @Override protected void rebuildWidgets(){
  var previous=getFocused();String label=previous instanceof net.minecraft.client.gui.components.AbstractWidget widget?widget.getMessage().getString():null;
  int cursor=previous instanceof net.minecraft.client.gui.components.EditBox edit?edit.getCursorPosition():-1;
  layout=null;super.rebuildWidgets();ButtonHints.apply(this);
  if(label!=null)for(var child:children())if(child.getClass()==previous.getClass()&&child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().equals(label)){setFocused(child);if(cursor>=0&&child instanceof net.minecraft.client.gui.components.EditBox edit)edit.setCursorPosition(Math.min(cursor,edit.getValue().length()));break;}
 }
 protected void onScrollEnd(){}
 protected void restoreScroll(int row){position=Math.max(0,row);firstRow=(int)position;}
 protected void resetScroll(){position=0;firstRow=0;}
 protected void rowsChanged(){rebuildWidgets();}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(super.mouseScrolled(x,y,dx,dy))return true;if(visibleRows>0&&dy!=0&&x>=scrollLeft()&&x<right+trackWidth()&&y>=top&&y<bottom){move(position-dy*3);return true;}return false;}
 protected int scrollLeft(){if(layout!=null)return layout.viewport().x();return children().stream().filter(c->c instanceof net.minecraft.client.gui.components.AbstractWidget w&&w.getY()>=top&&w.getY()<bottom).mapToInt(c->((net.minecraft.client.gui.components.AbstractWidget)c).getX()).min().orElse(right);}
 private int trackWidth(){return layout==null?dev.abros.anthub.core.ScrollLayout.TRACK_WIDTH:layout.track().width();}
 private int thumb(){return UiScrollbar.thumb(top,bottom,visibleRows,count);}
 private int thumbTop(){return UiScrollbar.thumbTop(top,bottom,visibleRows,count,position);}
 private void seek(double y){move((y-top-dragOffset)/Math.max(1,bottom-top-thumb())*Math.max(0,count-visibleRows));}
 @Override public boolean mouseClicked(double x,double y,int button){if(visibleRows>0&&button==0&&count>visibleRows&&x>=right&&x<right+trackWidth()&&y>=top&&y<bottom){int start=thumbTop();boolean grabbed=y>=start&&y<start+thumb();dragOffset=grabbed?y-start:thumb()/2.0;dragging=true;if(!grabbed)seek(y);return true;}return super.mouseClicked(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging&&button==0){seek(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){dragging=false;return super.mouseReleased(x,y,button);}
 @Override public boolean keyPressed(int key,int scan,int modifiers){if((getFocused() instanceof net.minecraft.client.gui.components.EditBox||getFocused() instanceof net.minecraft.client.gui.components.MultiLineEditBox)&&getFocused().keyPressed(key,scan,modifiers))return true;switch(key){case 266:move(position-visibleRows);return true;case 267:move(position+visibleRows);return true;case 268:move(0);return true;case 269:move(count);return true;default:return super.keyPressed(key,scan,modifiers);}}
 @Override public void render(GuiGraphics g,int x,int y,float delta){super.render(g,x,y,delta);if(layout!=null)UiScrollbar.draw(g,layout.track(),visibleRows,count,position);}
}
