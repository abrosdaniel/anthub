package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class HudEnhancementsTest {
 @Test void quietHoursCrossMidnight(){assertTrue(QuietHours.active(true,22,8,23));assertTrue(QuietHours.active(true,22,8,7));assertFalse(QuietHours.active(true,22,8,8));assertFalse(QuietHours.active(false,22,8,23));assertTrue(QuietHours.active(true,8,8,12));}
 @Test void deferredNoticeKeepsExactIdsAndReturnsAfterDelay(){var q=new HudNoticeQueue();q.add(new HudNoticeQueue.Notice("1","events","e","reminder","Event","Body",HudNoticeQueue.Priority.ORDINARY),0,2);var entry=q.advance(0,2,true).getFirst();assertTrue(q.defer(entry,1,600000));assertTrue(q.advance(600000,2,true).isEmpty());var restored=q.advance(600001,2,true).getFirst();assertEquals(java.util.List.of("1"),q.dismiss(restored));}
 @Test void urgentNoticeCannotBeDeferred(){var q=new HudNoticeQueue();q.add(new HudNoticeQueue.Notice("1","server","","server","Restart","Body",HudNoticeQueue.Priority.URGENT),0,2);assertFalse(q.defer(q.advance(0,2,true).getFirst(),1,600000));}
}
