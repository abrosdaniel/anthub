package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ScrollLayoutTest {
 @Test void trackAndContentStayInsideSmallAndLargeViewports(){
  for(int width=0;width<=2560;width++)for(int height:new int[]{0,1,12,96,720}){
   var owner=new NativeLayout.Box(17,23,width,height);var layout=ScrollLayout.fit(owner);
   assertTrue(layout.content().right()<=layout.track().x());
   assertTrue(layout.track().x()>=owner.x());assertEquals(owner.right(),layout.track().right());
   assertEquals(owner.bottom(),layout.track().bottom());assertTrue(layout.track().width()<=6);
  }
 }
 @Test void pickerReservesSpaceForTrackInsteadOfDrawingOutsideDialog(){
  var layout=ScrollLayout.fit(new NativeLayout.Box(200,100,420,220));
  assertEquals(610,layout.content().right());assertEquals(614,layout.track().x());assertEquals(620,layout.track().right());
 }
 @Test void thumbStaysInsideTrackAtEveryScrollPosition(){
  for(int height=0;height<=720;height++){
   var layout=ScrollLayout.fit(new NativeLayout.Box(4,7,300,height));
   for(double position:new double[]{-20,0,0.5,40,100,10000}){
    var thumb=layout.thumb(8,100,position);
    assertTrue(thumb.y()>=layout.track().y());assertTrue(thumb.bottom()<=layout.track().bottom());
    assertEquals(layout.track().x(),thumb.x());assertEquals(layout.track().right(),thumb.right());
   }
  }
  var layout=ScrollLayout.fit(new NativeLayout.Box(0,0,20,20));
  assertEquals(0,layout.thumb(0,100,0).height());assertEquals(0,layout.thumb(10,3,0).height());
  assertThrows(IllegalArgumentException.class,()->layout.thumb(10,100,Double.NaN));
 }
 @Test void callersCannotConstructAnEscapedTrack(){
  assertThrows(IllegalArgumentException.class,()->new ScrollLayout(new NativeLayout.Box(0,0,100,100),new NativeLayout.Box(0,0,90,100),new NativeLayout.Box(101,0,6,100)));
 }
}
