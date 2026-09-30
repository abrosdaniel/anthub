package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
/** Independent list scrolling, including a draggable thumb and trackpad fractions. */
final class RowViewport {
 private int count,visible=1,top,bottom,right;private double position;private boolean dragging;
 void layout(int count,int top,int bottom,int stride,int right){this.count=count;this.top=top;this.bottom=bottom;this.right=right;visible=Math.max(1,(bottom-top)/stride);move(position);}
 int first(){return (int)position;}int visible(){return visible;}
 void reset(){position=0;}
 void reveal(int index){if(index<first())move(index);else if(index>=first()+visible)move(index-visible+1);}
 private void move(double value){position=Math.max(0,Math.min(value,Math.max(0,count-visible)));}
 boolean scroll(double y,double dy){if(dy==0||count<=visible||y<top||y>=bottom)return false;move(position-dy*3);return true;}
 private int thumb(){return UiScrollbar.thumb(top,bottom,visible,count);}
 private void seek(double y){move((y-top-thumb()/2.0)/Math.max(1,bottom-top-thumb())*Math.max(0,count-visible));}
 boolean click(double x,double y,int button){if(button!=0||count<=visible||x<right||x>=right+7||y<top||y>=bottom)return false;dragging=true;seek(y);return true;}
 boolean drag(double y,int button){if(!dragging||button!=0)return false;seek(y);return true;}
 void release(){dragging=false;}
 void draw(GuiGraphics g){UiScrollbar.draw(g,right,top,bottom,visible,count,position);}
}
