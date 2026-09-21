package dev.abros.anthub.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class PlayerStatisticsTest {
 @TempDir Path temp;
 @Test void checkpointsAreIdempotentAndWorldIndependent()throws Exception{
  var db=TestDatabase.database(temp);var stats=new PlayerStatistics(db,PlayerStatistics.Settings.defaults());UUID player=UUID.randomUUID(),session=UUID.randomUUID();
  var c=new PlayerStatistics.Checkpoint(player,session,1000,61000,60000,2,true);stats.checkpoint(c);stats.checkpoint(c);
  stats.checkpoint(new PlayerStatistics.Checkpoint(player,session,1000,121000,120000,3,false));stats.checkpoint(c);
  var view=stats.read(List.of(player),Map.of()).get(player);assertEquals(120000,view.get("totalMillis").getAsLong());assertEquals(3,view.get("deaths").getAsLong());assertFalse(view.has("sessionMillis"));
  stats=new PlayerStatistics(db,PlayerStatistics.Settings.defaults());stats.checkpoint(new PlayerStatistics.Checkpoint(player,UUID.randomUUID(),900000,930000,30000,1,true));
  view=stats.read(List.of(player),Map.of(player,32000L)).get(player);assertEquals(150000,view.get("totalMillis").getAsLong());assertEquals(4,view.get("deaths").getAsLong());assertEquals(1000,view.get("firstJoin").getAsLong());assertEquals(32000,view.get("sessionMillis").getAsLong());
 }
 @Test void disabledCollectionKeepsHistoryPrivateAndDoesNotCountDowntime()throws Exception{
  var db=TestDatabase.database(temp);UUID player=UUID.randomUUID();var enabled=new PlayerStatistics(db,PlayerStatistics.Settings.defaults());
  enabled.checkpoint(new PlayerStatistics.Checkpoint(player,UUID.randomUUID(),1000,2000,1000,1,false));
  var disabled=new PlayerStatistics(db,new PlayerStatistics.Settings(false,false,false,false,false));disabled.checkpoint(new PlayerStatistics.Checkpoint(player,UUID.randomUUID(),3000,8000,5000,4,false));
  assertTrue(disabled.read(List.of(player),Map.of(player,10L)).get(player).isEmpty());
  enabled.checkpoint(new PlayerStatistics.Checkpoint(player,UUID.randomUUID(),10000,11000,1000,1,false));
  var view=enabled.read(List.of(player),Map.of()).get(player);assertEquals(2000,view.get("totalMillis").getAsLong());assertEquals(2,view.get("deaths").getAsLong());assertEquals(1000,view.get("firstJoin").getAsLong());
 }
 @Test void unknownPlayerDoesNotGetFabricatedHistoricalZero()throws Exception{
  var stats=new PlayerStatistics(TestDatabase.database(temp),PlayerStatistics.Settings.defaults());assertTrue(stats.read(List.of(UUID.randomUUID()),Map.of()).isEmpty());
 }
 @Test void correctionsPreserveSessionBaselineAndAudit()throws Exception{
  var db=TestDatabase.database(temp);var stats=new PlayerStatistics(db,PlayerStatistics.Settings.defaults());UUID player=UUID.randomUUID(),session=UUID.randomUUID();
  stats.checkpoint(new PlayerStatistics.Checkpoint(player,session,1000,61000,60000,2,true));
  var live=new PlayerStatistics.Checkpoint(player,session,1000,121000,120000,3,true);
  stats.correct(player,"totalMillis","set",3600000,"operator",live);
  stats.correct(player,"deaths","set",10,"operator");stats.correct(player,"firstJoin","set",500,"operator");
  stats.checkpoint(live);stats.checkpoint(new PlayerStatistics.Checkpoint(player,session,1000,181000,180000,4,false));
  var saved=stats.snapshot(player);assertEquals(3660000,saved.get("totalMillis").getAsLong());assertEquals(11,saved.get("deaths").getAsLong());assertEquals(500,saved.get("firstJoin").getAsLong());
  db.transaction(()->{try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT count(*) FROM records WHERE namespace='audit'")){rows.next();assertEquals(3,rows.getInt(1));}return null;});
 }
 @Test void rejectedCorrectionRollsBackCheckpointAndNeverCreatesPlayer()throws Exception{
  var db=TestDatabase.database(temp);var stats=new PlayerStatistics(db,PlayerStatistics.Settings.defaults());UUID player=UUID.randomUUID(),session=UUID.randomUUID();
  stats.checkpoint(new PlayerStatistics.Checkpoint(player,session,1000,2000,1000,0,true));
  assertThrows(IllegalArgumentException.class,()->stats.correct(player,"totalMillis","subtract",9000,"op",new PlayerStatistics.Checkpoint(player,session,1000,3000,2000,0,true)));
  assertEquals(1000,stats.snapshot(player).get("totalMillis").getAsLong());
  assertThrows(IllegalArgumentException.class,()->stats.correct(UUID.randomUUID(),"deaths","set",5,"op"));
  assertThrows(IllegalArgumentException.class,()->stats.correct(player,"firstJoin","set",System.currentTimeMillis()+86400000,"op"));
  stats.correct(player,"totalMillis","set",Long.MAX_VALUE,"op");assertThrows(IllegalArgumentException.class,()->stats.correct(player,"totalMillis","add",1,"op"));
 }
 @Test void lookupRequiresStatisticsAndDetectsAmbiguousNames()throws Exception{
  var db=TestDatabase.database(temp);var stats=new PlayerStatistics(db,PlayerStatistics.Settings.defaults());var community=new CommunityStore(db,CommunityStore.defaults());
  UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
  community.seen(new CommunityStore.Actor(a.toString(),"Player",false,false));community.seen(new CommunityStore.Actor(b.toString(),"PLAYER",false,false));community.seen(new CommunityStore.Actor(c.toString(),"Other",false,false));
  for(UUID id:List.of(a,b))stats.checkpoint(new PlayerStatistics.Checkpoint(id,UUID.randomUUID(),1000,2000,1000,0,false));
  assertEquals(2,stats.find("player",true).size());assertEquals(a,stats.find(a.toString(),true).getFirst().id());assertEquals(2,stats.find("pla",false).size());assertTrue(stats.find("Other",true).isEmpty());assertTrue(stats.find("' OR 1=1 --",false).isEmpty());
 }
}
