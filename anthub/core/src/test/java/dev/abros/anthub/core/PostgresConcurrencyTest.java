package dev.abros.anthub.core;

import com.google.gson.*;
import dev.abros.anthub.core.auth.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class PostgresConcurrencyTest {
    @TempDir Path identity;
    PgDatabase pool;CommunityStore store;
    @BeforeEach void setup()throws Exception{pool=TestDatabase.database(identity);store=new CommunityStore(pool,CommunityStore.defaults());}
    private static JsonObject request(String section,String op,String id){var j=new JsonObject();j.addProperty("section",section);j.addProperty("op",op);j.addProperty("id",id);return j;}
    @Test void threeHundredConcurrentVotesAreNotLost()throws Exception{
        var admin=new CommunityStore.Actor(UUID.randomUUID().toString(),"Admin",true,true);var create=request("polls","create","");create.addProperty("title","Concurrent vote");create.addProperty("description","Pool and row-lock stress test");create.addProperty("endsAt",Instant.now().plusSeconds(600).toString());create.addProperty("liveResults",true);var options=new JsonArray();options.add("Yes");options.add("No");create.add("options",options);
        String id=store.request(admin,create).getAsJsonObject("detail").get("id").getAsString();long started=System.nanoTime();
        try(var workers=Executors.newFixedThreadPool(16)){
            var jobs=new ArrayList<Callable<Void>>();for(int i=0;i<300;i++){int n=i;jobs.add(()->{var vote=request("polls","vote",id);var choices=new JsonArray();choices.add(n%2);vote.add("choices",choices);store.request(new CommunityStore.Actor(UUID.randomUUID().toString(),"Player"+n,false,false),vote);return null;});}
            for(var job:workers.invokeAll(jobs))job.get(60,TimeUnit.SECONDS);
        }
        var result=store.request(admin,request("polls","detail",id)).getAsJsonObject("detail").getAsJsonArray("counts");assertEquals(150,result.get(0).getAsInt());assertEquals(150,result.get(1).getAsInt());assertEquals(0,pool.activeConnections());
        System.out.printf("300 concurrent vote requests: %.0f ms total%n",(System.nanoTime()-started)/1e6);
    }
    @Test void notificationSummaryRespectsMuteAndRecipientForThreeHundredPlayers()throws Exception{
        var players=new ArrayList<UUID>();for(int i=0;i<300;i++){UUID id=UUID.randomUUID();players.add(id);store.externalNotice(id.toString(),"Personal");}
        long start=System.nanoTime();var summary=store.notificationSummary(players);assertEquals(300,summary.size());for(var entry:summary.values()){assertEquals(1,entry.unread());assertTrue(entry.sequence()>0);}System.out.printf("300-player notification aggregate: %.1f ms%n",(System.nanoTime()-start)/1e6);
    }
    @Test void invitationIsConsumedExactlyOnceAcrossStoreInstances()throws Exception{
        var first=new AuthStore(pool);var second=new AuthStore(pool);String uuid=UUID.randomUUID().toString();char[] password="a long test password".toCharArray();first.register("Alice",uuid,password,0);String token=first.invite("console","Alice",1);
        try(var workers=Executors.newFixedThreadPool(2)){
            var jobs=List.<Callable<Boolean>>of(()->reset(first,uuid,token,password),()->reset(second,uuid,token,password));int success=0;for(var job:workers.invokeAll(jobs))if(job.get())success++;assertEquals(1,success);
        }assertEquals(1,first.account("Alice").generation());
    }
    private static boolean reset(AuthStore store,String uuid,String token,char[] password)throws Exception{try{store.reset("Alice",uuid,token,password,2);return true;}catch(IllegalArgumentException expected){return false;}}
    @Test void sqlInputsCannotChangeScope()throws Exception{
        store.seen(new CommunityStore.Actor(UUID.randomUUID().toString(),"Safe",false,false));assertTrue(store.people("' OR 1=1 --",0).isEmpty());assertEquals(1,store.people("Safe",0).size());
        assertThrows(IllegalArgumentException.class,()->new DatabaseSettings("localhost/?password=x",5432,"anthub","anthub","secret","disable","",8));
    }
    @Test void batchSessionCheckRejectsRevokedExpiredAndWrongOwnerDevices()throws Exception{
        var auth=new AuthStore(pool);String uuid=UUID.randomUUID().toString();auth.register("Alice",uuid,"a long test password".toCharArray(),0);var device=auth.remember("Alice","Laptop",1);
        var valid=new AuthStore.SessionKey("Alice",0,device.id());var wrong=new AuthStore.SessionKey("Bob",0,device.id());assertEquals(Set.of(valid),auth.validSessions(List.of(valid,wrong),2));
        auth.revoke("Alice",device.id(),3);assertTrue(auth.validSessions(List.of(valid),4).isEmpty());
        var passwordSession=new AuthStore.SessionKey("Alice",0,"");assertTrue(auth.validSessions(List.of(passwordSession),4).contains(passwordSession));auth.block("console","Alice",true,5);assertTrue(auth.validSessions(List.of(passwordSession),6).isEmpty());
    }
}
