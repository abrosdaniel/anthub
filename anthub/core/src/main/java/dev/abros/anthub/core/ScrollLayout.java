package dev.abros.anthub.core;

/** Bounded list geometry: content and scrollbar share one owner rectangle. */
public record ScrollLayout(NativeLayout.Box viewport, NativeLayout.Box content, NativeLayout.Box track) {
 public static final int TRACK_WIDTH=6, GAP=4;
 public ScrollLayout {
  if(!inside(viewport,content)||!inside(viewport,track)||content.right()>track.x())throw new IllegalArgumentException("List geometry escapes its owner");
 }
 private static boolean inside(NativeLayout.Box owner,NativeLayout.Box child){return child.x()>=owner.x()&&child.y()>=owner.y()&&child.right()<=owner.right()&&child.bottom()<=owner.bottom();}
 public NativeLayout.Box thumb(int visible,int count,double position){
  if(visible<0||count<0||!Double.isFinite(position))throw new IllegalArgumentException("Invalid scroll state");
  if(count<=visible||visible==0)return new NativeLayout.Box(track.x(),track.y(),track.width(),0);
  int size=Math.min(track.height(),Math.max(12,(int)((long)track.height()*visible/Math.max(1,count))));
  int range=count-visible;double offset=Math.max(0,Math.min(position,range));
  int top=track.y()+(int)((track.height()-size)*offset/range);
  return new NativeLayout.Box(track.x(),top,track.width(),size);
 }

 public static ScrollLayout fit(NativeLayout.Box viewport) {
  int trackWidth=Math.min(TRACK_WIDTH,viewport.width());
  int gap=Math.min(GAP,viewport.width()-trackWidth);
  int contentWidth=viewport.width()-trackWidth-gap;
  return new ScrollLayout(viewport,new NativeLayout.Box(viewport.x(),viewport.y(),contentWidth,viewport.height()),
   new NativeLayout.Box(viewport.right()-trackWidth,viewport.y(),trackWidth,viewport.height()));
 }
}
