package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HudNoticeQueueTest {
 private HudNoticeQueue.Notice notice(String id,String target,HudNoticeQueue.Priority priority){return new HudNoticeQueue.Notice(id,"events",target,"reminder","Событие","Через 10 минут",priority);}
 @Test void expiresWithoutReadAndDoesNotReplayDuplicates(){var q=new HudNoticeQueue();q.add(notice("1","a",HudNoticeQueue.Priority.ORDINARY),0,2);var e=q.advance(0,2,true).getFirst();assertEquals(1,e.remaining(0));assertTrue(q.advance(6000,2,true).isEmpty());q.add(notice("1","a",HudNoticeQueue.Priority.ORDINARY),7000,2);assertTrue(q.advance(7000,2,true).isEmpty());}
 @Test void mergesObjectEventsAndDismissReturnsAllIds(){var q=new HudNoticeQueue();q.add(notice("1","a",HudNoticeQueue.Priority.ORDINARY),0,2);q.add(notice("2","a",HudNoticeQueue.Priority.ORDINARY),1,2);var e=q.advance(1,2,true).getFirst();assertEquals(2,e.count());assertEquals(java.util.List.of("1","2"),q.dismiss(e));assertTrue(q.advance(1,2,true).isEmpty());}
 @Test void urgentBypassesQueueAndDisplacedToastGetsFullDuration(){var q=new HudNoticeQueue();q.add(notice("1","a",HudNoticeQueue.Priority.ORDINARY),0,1);q.add(notice("2","b",HudNoticeQueue.Priority.ORDINARY),1,1);q.add(notice("3","c",HudNoticeQueue.Priority.URGENT),2,1);assertEquals("3",q.advance(2,1,true).getFirst().notice().id());assertEquals("1",q.advance(9002,1,true).getFirst().notice().id());assertEquals(1,q.advance(9002,1,true).getFirst().remaining(9002));}
 @Test void pausedMenuDoesNotConsumeLifetime(){var q=new HudNoticeQueue();q.add(notice("1","a",HudNoticeQueue.Priority.ORDINARY),0,2);q.advance(1000,2,false);var e=q.advance(21000,2,true).getFirst();assertEquals(5/6d,e.remaining(21000),.001);}
 @Test void burstsStayBoundedAndSummarizeOverflow(){var q=new HudNoticeQueue();for(int i=0;i<100;i++)q.add(notice(""+i,""+i,HudNoticeQueue.Priority.ORDINARY),0,2);boolean summary=false;for(int n=1;n<25;n++){var entries=q.advance(n*6000,2,true);assertTrue(entries.size()<=2);summary|=entries.stream().anyMatch(e->e.notice().event().equals("summary"));}assertTrue(summary);}
}
