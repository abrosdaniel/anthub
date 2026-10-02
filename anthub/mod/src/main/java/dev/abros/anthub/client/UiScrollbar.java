package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
/** Same thumb geometry and colours for independent lists and scrolling screens. */
final class UiScrollbar {
 private UiScrollbar(){}
 private static dev.abros.anthub.core.ScrollLayout geometry(int top,int bottom){
  var track=new dev.abros.anthub.core.NativeLayout.Box(0,top,6,Math.max(0,bottom-top));
  return new dev.abros.anthub.core.ScrollLayout(track,new dev.abros.anthub.core.NativeLayout.Box(0,top,0,track.height()),track);
 }
 static int thumb(int top,int bottom,int visible,int count){return geometry(top,bottom).thumb(visible,count,0).height();}
 static int thumbTop(int top,int bottom,int visible,int count,double position){return geometry(top,bottom).thumb(visible,count,position).y();}
 static void draw(GuiGraphics g,dev.abros.anthub.core.NativeLayout.Box track,int visible,int count,double position){
  if(count<=visible||visible==0||track.height()==0||track.width()==0)return;
  var geometry=new dev.abros.anthub.core.ScrollLayout(track,new dev.abros.anthub.core.NativeLayout.Box(track.x(),track.y(),0,track.height()),track);
  var thumb=geometry.thumb(visible,count,position);
  draw(g,track,thumb);
 }
 static void draw(GuiGraphics g,dev.abros.anthub.core.NativeLayout.Box track,dev.abros.anthub.core.NativeLayout.Box thumb){
  if(thumb.x()<track.x()||thumb.right()>track.right()||thumb.y()<track.y()||thumb.bottom()>track.bottom())throw new IllegalArgumentException("Scrollbar thumb escaped track");
  g.fill(track.x(),track.y(),track.right(),track.bottom(),UiPalette.scrollTrack());
  g.fill(thumb.x(),thumb.y(),thumb.right(),thumb.bottom(),UiPalette.scrollThumb());
 }
}
