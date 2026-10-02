package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import static dev.abros.anthub.core.NativeLayout.Track.*;
import static org.junit.jupiter.api.Assertions.*;
class NativeLayoutTest {
 @Test void weightedTracksFillTheirContainerWithoutOverlap(){for(int length=0;length<=1280;length++){var area=new NativeLayout.Box(11,17,length,360);var tracks=NativeLayout.row(area,12,fixed(136),flex(1),flex(2));assertEquals(area.right(),tracks.getLast().right());for(int i=0;i<tracks.size();i++){var box=tracks.get(i);assertTrue(box.width()>=0);assertTrue(box.x()>=area.x());assertTrue(box.right()<=area.right());if(i>0)assertTrue(tracks.get(i-1).right()<=box.x());}}}
 @Test void verticalTracksShrinkOnSmallWindows(){for(int height=0;height<=720;height++){var area=new NativeLayout.Box(8,9,300,height);var tracks=NativeLayout.column(area,6,fixed(20),flex(1),fixed(10),fixed(20));assertEquals(area.bottom(),tracks.getLast().bottom());for(int i=0;i<tracks.size();i++){var box=tracks.get(i);assertTrue(box.height()>=0);assertTrue(box.bottom()<=area.bottom());if(i>0)assertTrue(tracks.get(i-1).bottom()<=box.y());}}}
 @Test void fixedRowsKeepTrailingSpaceAndInsetsRemainBounded(){var row=NativeLayout.row(new NativeLayout.Box(10,20,300,24),8,fixed(80),fixed(90));assertEquals(188,row.getLast().right());var tiny=new NativeLayout.Box(0,0,3,1).inset(8);assertEquals(1,tiny.width());assertEquals(1,tiny.height());assertFalse(tiny.contains(tiny.right(),tiny.y()));assertThrows(IllegalArgumentException.class,()->fixed(-1));assertThrows(IllegalArgumentException.class,()->tiny.inset(-1));assertThrows(IllegalArgumentException.class,()->new NativeLayout.Track(10,2));}
}
