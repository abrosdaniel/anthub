package dev.abros.anthub.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class ModerationVotesTest {
 @TempDir Path temp;ModerationVotes votes;UUID initiator=UUID.randomUUID(),target=UUID.randomUUID();Set<UUID> eligible;long now;
 @BeforeEach void setup()throws Exception{votes=new ModerationVotes(TestDatabase.database(temp),new ModerationVotes.Settings(true,5,120,0,30,60,30,15,true,true,true));eligible=new HashSet<>();eligible.add(initiator);while(eligible.size()<5)eligible.add(UUID.randomUUID());now=System.currentTimeMillis();}
 JsonObject start(boolean protectedTarget,boolean voice,String action)throws Exception{var request=new JsonObject();request.addProperty("action","moderationVote");request.addProperty("op","start");request.addProperty("operationId",UUID.randomUUID().toString());request.addProperty("issuedAt",System.currentTimeMillis());return votes.start(initiator,target,"Target",action,"Нарушение правил",eligible,0,protectedTarget,voice,request,now);}
 @Test void thresholdsRoundUpAndRequireParticipation(){assertFalse(ModerationVotes.passed(0,0,5));assertFalse(ModerationVotes.passed(2,0,5));assertTrue(ModerationVotes.passed(3,1,5));assertFalse(ModerationVotes.passed(3,2,5));assertTrue(ModerationVotes.passed(4,2,6));}
 @Test void ballotsArePrivateAndElectorateIsFrozen()throws Exception{var view=start(false,true,"kick");String id=Json.str(view,"id");assertFalse(view.has("eligible"));assertFalse(view.has("ballots"));assertFalse(view.has("initiator"));assertThrows(IllegalArgumentException.class,()->votes.vote(UUID.randomUUID(),id,true,now+1));assertThrows(IllegalArgumentException.class,()->votes.vote(target,id,true,now+1));votes.vote(initiator,id,true,now+1);votes.vote(initiator,id,false,now+2);view=votes.current(initiator);assertEquals(0,view.get("yes").getAsInt());assertEquals(1,view.get("no").getAsInt());assertFalse(view.get("myVote").getAsBoolean());}
 @Test void protectedOrUnavailableTargetsCannotStart()throws Exception{assertThrows(IllegalArgumentException.class,()->start(true,true,"ban"));assertThrows(IllegalArgumentException.class,()->start(false,false,"mute"));assertTrue(votes.current(initiator).isEmpty());}
 @Test void claimIsExactlyOnceAndFinishDoesNotOverwriteOutcome()throws Exception{String id=Json.str(start(false,true,"ban"),"id");for(UUID player:eligible)votes.vote(player,id,true,now+1);assertNull(votes.claim(now+1000));
  try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->votes.claim(now+120001));var b=pool.submit(()->votes.claim(now+120001));assertEquals(1,(a.get()==null?0:1)+(b.get()==null?0:1));}
  assertThrows(IllegalArgumentException.class,()->votes.cancel(id,"Moderator","Поздно",now+120002));votes.finish(id,true,"Применено",now+120003);votes.finish(id,false,"Повтор",now+120004);assertEquals("applied",Json.str(votes.current(initiator),"status"));assertEquals("Применено",Json.str(votes.current(initiator),"outcome"));
 }
 @Test void cancellationAndRestartNeverApplyDeferredPenalty()throws Exception{String id=Json.str(start(false,true,"kick"),"id");for(UUID player:eligible)votes.vote(player,id,true,now+1);votes.cancel(id,"Moderator","Ошибка",now+2);assertNull(votes.claim(now+120001));assertEquals("cancelled",Json.str(votes.current(initiator),"status"));assertThrows(IllegalArgumentException.class,()->start(false,true,"kick"));}
 @Test void restartDuringApplyRequiresReview()throws Exception{String id=Json.str(start(false,true,"kick"),"id");for(UUID player:eligible)votes.vote(player,id,true,now+1);assertNotNull(votes.claim(now+120001));votes.restart();assertEquals("uncertain",Json.str(votes.current(initiator),"status"));assertNull(votes.claim(now+999999));}
 @Test void restartCancelsOpenVote()throws Exception{start(false,true,"kick");votes.restart();assertEquals("cancelled",Json.str(votes.current(initiator),"status"));assertNull(votes.claim(now+999999));}
 @Test void oneActiveVoteAndNoLateBallots()throws Exception{String id=Json.str(start(false,true,"kick"),"id");assertThrows(IllegalArgumentException.class,()->start(false,true,"ban"));assertThrows(IllegalArgumentException.class,()->votes.vote(initiator,id,true,now+120000));assertNull(votes.claim(now+120001));assertEquals("rejected",Json.str(votes.current(initiator),"status"));}
}
