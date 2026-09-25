package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UiLayoutTest {
 @Test void rowsNeverOverlapOrLeaveContainer(){for(int width:new int[]{96,128,202,280,600})for(int count=1;count<=5;count++){var boxes=UiLayout.row(12,32,width,count,4,20);assertEquals(12,boxes[0].x());assertEquals(12+width,boxes[count-1].right());for(int i=0;i<count;i++){assertTrue(boxes[i].width()>0);for(int j=i+1;j<count;j++)assertFalse(boxes[i].overlaps(boxes[j]));}}}
 @Test void calendarFitsSupportedGuiSizes(){for(int h:new int[]{240,270,360,480,720})for(int w:new int[]{320,427,640,854,1280}){var c=UiLayout.calendar(w,h);assertTrue(c.left()>=0);assertTrue(c.left()+c.width()<=w);assertTrue(c.gridTop()+c.cellHeight()*6<=c.shortcuts());assertTrue(c.shortcuts()+20<=c.time());assertTrue(c.time()+20<=c.footer(),"height "+h);assertTrue(c.footer()+20<=h);}}
}
