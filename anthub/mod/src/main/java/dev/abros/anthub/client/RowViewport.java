package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
/** Independent list scrolling, including a draggable thumb and trackpad fractions. */
final class RowViewport {
 private int count,visible=1,top,bottom,right;private double position;private boolean dragging;private double dragOffset;private dev.abros.anthub.core.ScrollLayout layout;
 void layout(int count,dev.abros.anthub.core.NativeLayout.Box viewport,int stride){
  if(count<0||stride<=0)throw new IllegalArgumentException("Invalid list dimensions");
  layout=dev.abros.anthub.core.ScrollLayout.fit(viewport);this.count=count;top=viewport.y();bottom=viewport.bottom();right=layout.track().x();visible=Math.max(0,viewport.height()/stride);move(position);
 }
 dev.abros.anthub.core.ScrollLayout geometry(){return layout;}
 int first(){return (int)position;}int visible(){return visible;}
 void reset(){position=0;}
 void reveal(int index){if(index<first())move(index);else if(index>=first()+visible)move(index-visible+1);}
 private void move(double value){position=Math.max(0,Math.min(value,Math.max(0,count-visible)));}
 boolean scroll(double y,double dy){if(visible==0||dy==0||count<=visible||y<top||y>=bottom)return false;move(position-dy*3);return true;}
 private int thumb(){return UiScrollbar.thumb(top,bottom,visible,count);}
 private void seek(double y){move((y-top-dragOffset)/Math.max(1,bottom-top-thumb())*Math.max(0,count-visible));}
 boolean click(double x,double y,int button){if(visible==0||button!=0||count<=visible||x<right||x>=right+layout.track().width()||y<top||y>=bottom)return false;int start=UiScrollbar.thumbTop(top,bottom,visible,count,position);boolean grabbed=y>=start&&y<start+thumb();dragOffset=grabbed?y-start:thumb()/2.0;dragging=true;if(!grabbed)seek(y);return true;}
 boolean drag(double y,int button){if(!dragging||button!=0)return false;seek(y);return true;}
 void release(){dragging=false;}
 void draw(GuiGraphics g){if(layout!=null)UiScrollbar.draw(g,layout.track(),visible,count,position);}
}
