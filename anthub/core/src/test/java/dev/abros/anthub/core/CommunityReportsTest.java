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
        store.prune(now+Duration.ofDays(31).toMillis());assertTrue(store.list(0).isEmpty());
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
