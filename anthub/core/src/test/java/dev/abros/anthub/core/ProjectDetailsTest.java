package dev.abros.anthub.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ProjectDetailsTest {
    @TempDir Path game;
    static final String REPO="https://github.com/example/project";
    static final class Source extends Remote {
        String name="Before",address="old.example.org:25565";boolean offline,invalid;int requests;
        @Override public byte[] bytes(String url,int limit)throws java.io.IOException {
            requests++;assertEquals(Repositories.raw(REPO,"anthub.json"),url);
            if(offline)throw new Unavailable("offline");
            var json=Json.parse("{\"name\":\"sample\",\"version\":\"1.0.0\",\"server\":{\"address\":\"sample.org\"},\"minecraft\":{\"version\":\"1.21.1\",\"loaderVersion\":\"21.1.250\"},\"components\":[]}");
            json.addProperty("name",name);json.getAsJsonObject("server").addProperty("address",address);
            if(invalid)json.remove("server");
            return Json.GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        }
    }
    @Test void updatesWithoutPackVersionChangeAndSurvivesOfflineRestart()throws Exception {
        var source=new Source();var details=new ProjectDetails(game,source);
        assertEquals("old.example.org:25565",details.refresh(REPO).address());
        source.name="After";source.address="new.example.org:25566";
        assertEquals("After",details.refresh(REPO).name());
        source.offline=true;var restarted=new ProjectDetails(game,source);
        assertEquals("new.example.org:25566",restarted.refresh(REPO).address());
    }
    @Test void invalidUpdatesPreserveLastValidCache()throws Exception {
        var source=new Source();var details=new ProjectDetails(game,source);details.refresh(REPO);
        source.invalid=true;assertThrows(IllegalArgumentException.class,()->details.refresh(REPO));
        source.offline=true;assertEquals("Before",new ProjectDetails(game,source).refresh(REPO).name());
    }
    @Test void rejectsUrlInsteadOfMinecraftAddress()throws Exception {
        var source=new Source();source.address="https://evil.example/path";
        assertThrows(IllegalArgumentException.class,()->new ProjectDetails(game,source).refresh(REPO));
    }
    @Test void hubUsesNewAddressWithoutChangingInstalledLock()throws Exception {
        byte[] bytes;try(var input=getClass().getResourceAsStream("/fixtures/lock.json")){bytes=input.readAllBytes();}
        var lock=Json.parse(new String(bytes,StandardCharsets.UTF_8));var state=new com.google.gson.JsonObject();state.add("lock",lock);state.addProperty("lockSha256",Hashes.sha256(bytes));Json.write(game.resolve("anthub/state.json"),state);
        Json.write(game.resolve("anthub/preferences.json"),java.util.Map.of("repositories",java.util.List.of(REPO)));
        var source=new Source();new ProjectDetails(game,source).refresh(REPO);
        var hub=new Hub(game,"1.0.0","21.1.250");String old=hub.active().servers().getFirst().address();
        assertEquals("old.example.org:25565",hub.selectedServer(hub.active()).address());
        assertEquals("Before",hub.menuProjectName());assertEquals(Hashes.sha256(bytes),hub.activeHash());
        assertEquals(old,hub.active().servers().getFirst().address());
    }
    @Test void connectionReusesRecentCheckAndRefreshesAfterOneMinute()throws Exception {
        var time=new java.util.concurrent.atomic.AtomicLong();var source=new Source();
        var details=new ProjectDetails(game,source,time::get);
        details.refresh(REPO);source.address="new.example.org:25566";
        time.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(59));
        assertEquals("old.example.org:25565",details.refreshIfStale(REPO).address());assertEquals(1,source.requests);
        time.set(java.util.concurrent.TimeUnit.MINUTES.toNanos(1));
        assertEquals("new.example.org:25566",details.refreshIfStale(REPO).address());assertEquals(2,source.requests);
    }
    @Test void manualRefreshBypassesCooldown()throws Exception {
        var source=new Source();var details=new ProjectDetails(game,source,()->0L);
        details.refresh(REPO);source.name="After";
        assertEquals("After",details.refresh(REPO).name());assertEquals(2,source.requests);
        details.refreshIfStale(REPO);assertEquals(2,source.requests);
    }
    @Test void restartDoesNotTreatDiskCacheAsFreshNetworkResult()throws Exception {
        var source=new Source();new ProjectDetails(game,source,()->0L).refresh(REPO);
        source.name="After";var restarted=new ProjectDetails(game,source,()->0L);restarted.load(REPO);
        assertEquals("After",restarted.refreshIfStale(REPO).name());assertEquals(2,source.requests);
    }
    @Test void offlineAttemptDoesNotPreventNextConnectionFromChecking()throws Exception {
        var source=new Source();var details=new ProjectDetails(game,source,()->0L);source.offline=true;
        assertNull(details.refreshIfStale(REPO));source.offline=false;
        assertEquals("Before",details.refreshIfStale(REPO).name());assertEquals(2,source.requests);
    }
}
