package dev.abros.anthub.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class CommunityReadPerformanceTest {
 @TempDir Path temp;
 @Test void repeatedPagedReadsRemainStable()throws Exception{
  var store=new CommunityStore(TestDatabase.database(temp),CommunityStore.defaults());var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"PerfReader",true,true);store.seen(actor);
  for(int i=0;i<40;i++){var author=new CommunityStore.Actor(UUID.randomUUID().toString(),"Author"+i,false,false);store.seen(author);var q=new JsonObject();q.addProperty("section","board");q.addProperty("op","create");q.addProperty("title","Entry "+i);q.addProperty("description","Representative description ".repeat(20));q.addProperty("days",7);q.addProperty("type","Предложение");store.request(author,q);}
  var q=new JsonObject();q.addProperty("section","board");q.addProperty("op","list");var baseline=store.request(actor,q);assertFalse(baseline.getAsJsonArray("entries").isEmpty());
  for(int i=0;i<10;i++)store.request(actor,q);var times=new long[100];for(int i=0;i<times.length;i++){long start=System.nanoTime();var result=store.request(actor,q);times[i]=System.nanoTime()-start;assertEquals(baseline.getAsJsonArray("entries"),result.getAsJsonArray("entries"));}
  Arrays.sort(times);System.out.printf(Locale.ROOT,"ANTHUB_READ_BENCH records=40 reads=100 median=%.2fms p95=%.2fms max=%.2fms%n",times[50]/1e6,times[95]/1e6,times[99]/1e6);
 }
}
