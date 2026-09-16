package dev.abros.anthub.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
/** Row scrolling retains fractional trackpad deltas, with a draggable scrollbar. */
abstract class ScrollScreen extends Screen {
 protected int firstRow,visibleRows=1;private int count,top,bottom,right;private double position;private boolean dragging;
 protected ScrollScreen(Component title){super(title);}
 protected void scrollArea(int count,int top,int bottom,int rowHeight,int right){this.count=count;this.top=top;this.bottom=bottom;this.right=right;visibleRows=Math.max(1,(bottom-top)/rowHeight);position=Math.max(0,Math.min(position,Math.max(0,count-visibleRows)));firstRow=(int)position;}
 private void move(double value){position=Math.max(0,Math.min(value,Math.max(0,count-visibleRows)));int next=(int)position;if(next!=firstRow){firstRow=next;rowsChanged();}}
 protected void rowsChanged(){rebuildWidgets();}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(y>=top&&y<bottom){move(position-dy*3);return true;}return super.mouseScrolled(x,y,dx,dy);}
 private int thumb(){return Math.max(12,(bottom-top)*visibleRows/Math.max(1,count));}
 private void seek(double y){move((y-top-thumb()/2.0)/Math.max(1,bottom-top-thumb())*Math.max(0,count-visibleRows));}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&count>visibleRows&&x>=right&&x<right+7&&y>=top&&y<bottom){dragging=true;seek(y);return true;}return super.mouseClicked(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging&&button==0){seek(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){dragging=false;return super.mouseReleased(x,y,button);}
 @Override public boolean keyPressed(int key,int scan,int modifiers){if((getFocused() instanceof net.minecraft.client.gui.components.EditBox||getFocused() instanceof net.minecraft.client.gui.components.MultiLineEditBox)&&getFocused().keyPressed(key,scan,modifiers))return true;switch(key){case 266:move(position-visibleRows);return true;case 267:move(position+visibleRows);return true;case 268:move(0);return true;case 269:move(count);return true;default:return super.keyPressed(key,scan,modifiers);}}
 @Override public void render(GuiGraphics g,int x,int y,float delta){super.render(g,x,y,delta);if(count>visibleRows){g.fill(right,top,right+6,bottom,0x88202020);int start=top+(int)((bottom-top-thumb())*position/(count-visibleRows));g.fill(right,start,right+6,start+thumb(),0xFFAAAAAA);}}
}
