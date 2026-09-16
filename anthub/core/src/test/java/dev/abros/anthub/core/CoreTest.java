package dev.abros.anthub.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
class CoreTest {
    @TempDir Path game;
    @Test void duplicateKeysRejected(){assertThrows(Exception.class,()->Json.parse("{\"a\":1,\"a\":2}"));}
    @Test void unsafePathsRejected(){for(String p:List.of("../mods/a.jar","mods/../a.jar","saves/world","config/anthub-client.toml","mods/CON.jar","mods/a.jar.","mods/a\\b.jar","mods//a.jar","/mods/a.jar"))assertThrows(IllegalArgumentException.class,()->SafePaths.validate(p),p);}
    @Test void symlinkEscapeRejected()throws Exception{Path outside=Files.createTempDirectory("anthub-outside");try{Files.createSymbolicLink(game.resolve("mods"),outside);assertThrows(Exception.class,()->SafePaths.resolve(game,"mods/a.jar"));}finally{Files.deleteIfExists(game.resolve("mods"));Files.delete(outside);}}
    @Test void repoIdentityNormalized(){assertEquals("https://github.com/owner/repo",Repositories.normalize("https://github.com/Owner/Repo.git/"));assertThrows(Exception.class,()->Repositories.normalize("https://user@github.com/a/b"));}
    private String object(String text)throws Exception{String h=Hashes.sha256(text.getBytes());Path p=game.resolve("anthub/cache/objects/"+h.substring(0,2)+"/"+h);Files.createDirectories(p.getParent());Files.writeString(p,text);return h;}
    private Planner.Plan plan(String before,String after){return new Planner.Plan(UUID.randomUUID().toString(),"project",List.of(new Planner.Change("mods/a.jar",before,after)),Map.of(),Set.of(),List.of(),0);}
    @Test void recoveryDoesNotUndoCommittedTransaction()throws Exception{
        Files.createDirectories(game.resolve("mods"));Files.writeString(game.resolve("mods/a.jar"),"old");String old=Hashes.sha256(game.resolve("mods/a.jar")),next=object("new");
        var tx=new Transactions(game);var p=plan(old,next);JsonObject state=new JsonObject();state.addProperty("version","new");tx.prepare(p,"{}".getBytes(),state);tx.apply(p.id());assertEquals("new",Files.readString(game.resolve("mods/a.jar")));tx.recover(p.id());assertEquals("new",Files.readString(game.resolve("mods/a.jar")));assertEquals("old",Files.readString(tx.directory(p.id()).resolve("preimages/0")));
    }
    @Test void injectedFailureRollsBack()throws Exception{
        String next=object("new");var tx=new Transactions(game);var p=plan(null,next);tx.prepare(p,"{}".getBytes(),new JsonObject());System.setProperty("anthub.test.failAfter","1");
        try{assertThrows(Exception.class,()->tx.apply(p.id()));assertFalse(Files.exists(game.resolve("mods/a.jar")));}finally{System.clearProperty("anthub.test.failAfter");}
    }
    @Test void abortReadyPreservesExternalChanges()throws Exception{
        var tx=new Transactions(game);var p=plan(null,object("new"));tx.prepare(p,new byte[0],new JsonObject());Files.createDirectories(game.resolve("mods"));Files.writeString(game.resolve("mods/a.jar"),"user");tx.abortReady(p.id());assertEquals("user",Files.readString(game.resolve("mods/a.jar")));assertFalse(Files.exists(game.resolve("anthub/pending.json")));
    }
    @Test void pendingTransactionCannotBeReplaced()throws Exception{
        var tx=new Transactions(game);var first=plan(null,object("first"));tx.prepare(first,new byte[0],new JsonObject());assertThrows(java.io.IOException.class,()->tx.prepare(plan(null,object("second")),new byte[0],new JsonObject()));assertEquals(first.id(),Json.str(Json.read(game.resolve("anthub/pending.json")),"id"));
    }
    @Test void changedFilePreventsMutation()throws Exception{
        String next=object("new");var tx=new Transactions(game);var p=plan(null,next);tx.prepare(p,"{}".getBytes(),new JsonObject());Files.createDirectories(game.resolve("mods"));Files.writeString(game.resolve("mods/a.jar"),"user");assertThrows(Exception.class,()->tx.apply(p.id()));assertEquals("user",Files.readString(game.resolve("mods/a.jar")));
    }

    @Test void oldCommittedRecoveryCannotClearNewPendingUpdate()throws Exception{
        var tx=new Transactions(game);var first=plan(null,object("first"));tx.prepare(first,new byte[0],new JsonObject());tx.apply(first.id());
        var second=plan(first.changes().getFirst().after(),object("second"));tx.prepare(second,new byte[0],new JsonObject());
        tx.recover(first.id());assertEquals(second.id(),Json.str(Json.read(game.resolve("anthub/pending.json")),"id"));
        tx.apply(first.id());assertEquals(second.id(),Json.str(Json.read(game.resolve("anthub/pending.json")),"id"));
        tx.apply(second.id());assertEquals("second",Files.readString(game.resolve("mods/a.jar")));
    }

    @Test void concurrentPreparationHasOnlyOneWinner()throws Exception{
        var first=plan(null,object("first"));var second=plan(null,object("second"));var latch=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var jobs=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for(var candidate:List.of(first,second))jobs.add(executor.submit(()->{latch.await();try{new Transactions(game).prepare(candidate,new byte[0],new JsonObject());return true;}catch(java.io.IOException busy){return false;}}));
            latch.countDown();int successes=0;for(var job:jobs)if(job.get())successes++;assertEquals(1,successes);
            String pending=Json.str(Json.read(game.resolve("anthub/pending.json")),"id");assertTrue(Set.of(first.id(),second.id()).contains(pending));
        }
    }
}
