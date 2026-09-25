package dev.abros.anthub.core;
/** Shared geometry in scaled GUI pixels; independent of the Minecraft renderer for regression tests. */
public final class UiLayout {
 private UiLayout(){}
 public record Rect(int x,int y,int width,int height){public int right(){return x+width;}public int bottom(){return y+height;}public boolean overlaps(Rect other){return x<other.right()&&right()>other.x&&y<other.bottom()&&bottom()>other.y;}}
 public static Rect[] row(int x,int y,int width,int count,int gap,int height){if(count<1||width<count)throw new IllegalArgumentException("Invalid row");gap=Math.max(0,Math.min(gap,(width-count)/Math.max(1,count-1)));var out=new Rect[count];int available=width-gap*(count-1);for(int i=0;i<count;i++){int start=available*i/count,end=available*(i+1)/count;out[i]=new Rect(x+start+i*gap,y,end-start,height);}return out;}
 public record Calendar(int left,int top,int width,int cellHeight,int gridTop,int shortcuts,int time,int footer){}
 public static Calendar calendar(int width,int height){int w=Math.min(280,width-24);int cell=Math.min(22,Math.max(14,(height-148)/6));int top=Math.max(14,(height-(128+cell*6))/2+10);int grid=top+36,shortcuts=grid+cell*6+4,time=shortcuts+24;return new Calendar((width-w)/2,top,w,cell,grid,shortcuts,time,time+26);}
}
