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
}
