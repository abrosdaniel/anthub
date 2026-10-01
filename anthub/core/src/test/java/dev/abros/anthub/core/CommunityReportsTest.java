package dev.abros.anthub.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("postgres")
class CommunityReportsTest {
    CommunityStore database;
    @org.junit.jupiter.api.BeforeEach void connect()throws Exception{database=new CommunityStore(TestDatabase.database(game),CommunityStore.defaults());}
    @org.junit.jupiter.api.AfterEach void disconnect()throws Exception{database.close();}
    @TempDir Path game;
    JsonObject report(){var j=new JsonObject();j.addProperty("message","Connection failed");j.addProperty("uuid","spoofed");j.addProperty("log","not allowed");j.addProperty("coreVersion","1.0.0");return j;}
    @Test void bulkRechecksRevisionsAndReplaysWithoutDuplicateChanges()throws Exception{
        var store=new CommunityReports(database);String actor=UUID.randomUUID().toString();long now=System.currentTimeMillis();String first=store.submit(UUID.randomUUID(),"First",report(),now),second=store.submit(UUID.randomUUID(),"Second",report(),now+1);
        var command=new JsonObject();command.addProperty("request",UUID.randomUUID().toString());command.addProperty("operation","bulkPreview");command.addProperty("change","resolved");var items=new com.google.gson.JsonArray();for(String id:java.util.List.of(first,second)){var item=new JsonObject();item.addProperty("id",id);item.addProperty("revision",1);items.add(item);}command.add("items",items);
        assertEquals(2,store.manageRequest(actor,"Admin",command).get("affected").getAsInt());store.reply(second,"Other","Waiting",false);
        command.addProperty("operation","bulkApply");command.addProperty("request",UUID.randomUUID().toString());command.addProperty("operationId",UUID.randomUUID().toString());command.addProperty("issuedAt",System.currentTimeMillis());var applied=store.manageRequest(actor,"Admin",command);assertEquals(1,applied.get("affected").getAsInt());assertEquals(1,applied.get("skipped").getAsInt());var replay=store.manageRequest(actor,"Admin",command);assertTrue(replay.get("replayed").getAsBoolean());assertEquals(applied.get("affected"),replay.get("affected"));assertEquals(1,store.queue(actor,"resolved","","").getAsJsonArray("entries").size());assertEquals(0,store.queue(actor,"new","","").getAsJsonArray("entries").size());
    }
    @Test void reportQueueFiltersAndCursorAreBoundToTheirQuery()throws Exception{
        var store=new CommunityReports(database);String actor=UUID.randomUUID().toString();long now=System.currentTimeMillis();for(int n=0;n<12;n++)store.submit(UUID.randomUUID(),"Player "+n,report(),now+n);
        var first=store.queue(actor,"new","Player","");assertEquals(10,first.getAsJsonArray("entries").size());String cursor=Json.str(first,"nextCursor");assertEquals(2,store.queue(actor,"new","Player",cursor).getAsJsonArray("entries").size());assertThrows(IllegalArgumentException.class,()->store.queue(actor,"resolved","Player",cursor));assertEquals(0,store.queue(actor,"mine","","").getAsJsonArray("entries").size());
    }
    @Test void dashboardPrioritizesUrgentThenUnassignedAndExcludesResolved()throws Exception{
        var store=new CommunityReports(database);long now=System.currentTimeMillis();
        String normal=store.submit(UUID.randomUUID(),"Normal",report(),now),urgent=store.submit(UUID.randomUUID(),"Urgent",report(),now+1),closed=store.submit(UUID.randomUUID(),"Closed",report(),now+2);
        var classify=new JsonObject();classify.addProperty("request",UUID.randomUUID().toString());classify.addProperty("id",urgent);classify.addProperty("revision",1);classify.addProperty("operation","classify");classify.addProperty("category","technical");classify.addProperty("priority","high");store.manageRequest(UUID.randomUUID().toString(),"Admin",classify);store.reply(closed,"Admin","Done",true);
        var dashboard=store.dashboard();assertEquals(2,dashboard.get("openReports").getAsInt());assertEquals(1,dashboard.get("highReports").getAsInt());var queue=dashboard.getAsJsonArray("attentionReports");assertEquals(2,queue.size());assertEquals(urgent,Json.str(queue.get(0).getAsJsonObject(),"id"));assertEquals(normal,Json.str(queue.get(1).getAsJsonObject(),"id"));assertTrue(queue.get(0).getAsJsonObject().has("revision"));
    }
    @Test void storesOnlyAllowedFieldsAndServerIdentity()throws Exception{
        var store=new CommunityReports(database);UUID player=UUID.randomUUID();String id=store.submit(player,"Player",report(),System.currentTimeMillis());
        var saved=store.list(0).get(0).getAsJsonObject();assertEquals(id,Json.str(saved,"id"));assertEquals(player.toString(),Json.str(saved,"uuid"));assertFalse(saved.has("log"));
    }
    @Test void rateLimitIsPerPlayer()throws Exception{
        var store=new CommunityReports(database);UUID p=UUID.randomUUID();long now=System.currentTimeMillis();store.submit(p,"Player",report(),now);
        assertThrows(IllegalArgumentException.class,()->store.submit(p,"Player",report(),now+500));store.submit(UUID.randomUUID(),"Other",report(),now+500);
        store.submit(p,"Player",report(),now+60001);assertEquals(3,store.list(0).size());
    }
    @Test void oversizedReportIsNotWritten()throws Exception{
        var store=new CommunityReports(database);var input=report();input.addProperty("message","x".repeat(1501));assertThrows(IllegalArgumentException.class,()->store.submit(UUID.randomUUID(),"Player",input,System.currentTimeMillis()));assertTrue(store.list(0).isEmpty());
    }
    @Test void retentionAndPaging()throws Exception{
        var store=new CommunityReports(database);long now=System.currentTimeMillis();for(int i=0;i<6;i++)store.submit(UUID.randomUUID(),"Player",report(),now+i);
        assertEquals(5,store.list(0).size());assertEquals(1,store.list(1).size());
        String resolved=Json.str(store.list(1).get(0).getAsJsonObject(),"id");store.reply(resolved,"Admin","Done",true);
        store.prune(now+Duration.ofDays(31).toMillis());assertEquals(5,store.list(0).size());assertTrue(store.list(1).isEmpty());
    }
    @Test void playersOnlySeeTheirOwnReportsAndReplies()throws Exception{
        var store=new CommunityReports(database);var owner=UUID.randomUUID();var other=UUID.randomUUID();long now=System.currentTimeMillis();
        String id=store.submit(owner,"Owner",report(),now);store.submit(other,"Other",report(),now);
        assertEquals(1,store.list(owner,0).size());assertEquals(1,store.list(other,0).size());
        store.reply(id,"Admin","Fixed",true);var saved=store.list(owner,0).get(0).getAsJsonObject();assertEquals("resolved",Json.str(saved,"status"));assertEquals("Fixed",Json.str(saved,"reply"));
        assertFalse(store.list(other,0).get(0).getAsJsonObject().has("reply"));
        assertThrows(IllegalArgumentException.class,()->store.reply("../outside","Admin","Reply",true));
        assertThrows(IllegalArgumentException.class,()->store.reply(id,"Admin","x".repeat(1501),true));
    }
    @Test void indexSurvivesRestartAndReturnedPagesCannotChangeIt()throws Exception{
        var store=new CommunityReports(database);UUID owner=UUID.randomUUID();String id=store.submit(owner,"Owner",report(),System.currentTimeMillis());
        store.list(owner,0).get(0).getAsJsonObject().addProperty("message","tampered");assertEquals("Connection failed",Json.str(store.list(owner,0).get(0).getAsJsonObject(),"message"));
        store.reply(id,"Admin","Fixed",true);database.close();database=new CommunityStore(TestDatabase.database(game),CommunityStore.defaults());var restarted=new CommunityReports(database);assertEquals("Fixed",Json.str(restarted.list(owner,0).get(0).getAsJsonObject(),"reply"));
    }
}
