package dev.abros.anthub.core;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ServerProjectPolicyTest {
    @TempDir Path game;
    static final String REPO="https://github.com/example/project";
    static class Source extends Remote {
        final Map<String,byte[]> values=new HashMap<>();boolean offline;int requests;
        @Override public byte[] bytes(String url,int limit)throws java.io.IOException {
            requests++;if(offline)throw new Unavailable("offline");
            if(!values.containsKey(url))throw new java.io.IOException("Unexpected URL");return values.get(url);
        }
    }
    JsonObject manifest()throws Exception {
        try(var input=getClass().getResourceAsStream("/fixtures/lock.json")){return Json.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
    }
    void publish(Source source,String version,int sequence)throws Exception {
        var lock=manifest();lock.getAsJsonObject("release").addProperty("version",version);lock.getAsJsonObject("release").addProperty("sequence",sequence);
        byte[] bytes=Json.GSON.toJson(lock).getBytes(StandardCharsets.UTF_8);String url=REPO+"/releases/download/pack-v"+version+"/anthub.lock.json";
        var pointer=new JsonObject();pointer.addProperty("schemaVersion",1);pointer.addProperty("repository",REPO);pointer.addProperty("channel","stable");pointer.addProperty("version",version);pointer.addProperty("sequence",sequence);pointer.addProperty("lockUrl",url);pointer.addProperty("lockSha256",Hashes.sha256(bytes));pointer.addProperty("publishedAt","2026-09-16T00:00:00Z");
        source.values.put(Repositories.raw(REPO,"channels/stable.json"),Json.GSON.toJson(pointer).getBytes(StandardCharsets.UTF_8));source.values.put(url,bytes);
    }
    JsonObject state(ServerProjectPolicy policy){
        var state=new JsonObject();state.addProperty("repository",REPO);state.addProperty("lockSha256",policy.hash());state.addProperty("protocolVersion",1);state.addProperty("coreVersion","1.0.0");state.addProperty("requiredFilesDigest",policy.digest());return state;
    }
    @Test void loadsAutomaticallyAndCachesForOfflineStartup()throws Exception {
        var source=new Source();publish(source,"1.0.0",1);
        var live=ServerProjectPolicy.load(new RepositoryClient(game,source),REPO,true);
        assertEquals("1.0.0",live.version());assertEquals("survival",live.serverId());assertFalse(live.offline());assertEquals("",live.verify(state(live)));
        source.offline=true;var cached=ServerProjectPolicy.load(new RepositoryClient(game,source),REPO,true);
        assertTrue(cached.offline());assertEquals(live.hash(),cached.hash());assertEquals(live.release().checkedAt(),cached.release().checkedAt());
    }
    @Test void offlineFirstStartupCannotEnableRequiredPack()throws Exception {
        var source=new Source();source.offline=true;
        assertThrows(IllegalStateException.class,()->ServerProjectPolicy.load(new RepositoryClient(game,source),REPO,true));
    }
    @Test void requiredPackNeedsProjectButOptionalBlankMakesNoRequests()throws Exception {
        var source=new Source();var client=new RepositoryClient(game,source);
        assertThrows(IllegalArgumentException.class,()->ServerProjectPolicy.load(client,"",true));
        assertFalse(ServerProjectPolicy.load(client,"",false).required());assertEquals(0,source.requests);
    }
    @Test void disabledVerificationDoesNotRejectClientsWithWrongPack()throws Exception {
        var source=new Source();publish(source,"1.0.0",1);
        var policy=ServerProjectPolicy.load(new RepositoryClient(game,source),REPO,false);
        assertEquals("1.0.0",policy.version());assertEquals("",policy.verify(new JsonObject()));
        source.offline=true;var missing=ServerProjectPolicy.load(new RepositoryClient(game.resolve("other"),source),REPO,false);
        assertFalse(missing.problem().isEmpty());assertEquals("",missing.verify(new JsonObject()));
    }
    @Test void newPublicationOnlyAffectsNextPolicyLoad()throws Exception {
        var source=new Source();publish(source,"1.0.0",1);var client=new RepositoryClient(game,source);
        var first=ServerProjectPolicy.load(client,REPO,true);publish(source,"1.1.0",2);
        assertEquals("1.0.0",first.version());assertEquals("",first.verify(state(first)));
        var second=ServerProjectPolicy.load(client,REPO,true);assertEquals("1.1.0",second.version());assertNotEquals(first.hash(),second.hash());assertFalse(second.verify(state(first)).isEmpty());
    }
    @Test void tamperedReleaseDoesNotSilentlyUsePreviousCache()throws Exception {
        var source=new Source();publish(source,"1.0.0",1);var client=new RepositoryClient(game,source);ServerProjectPolicy.load(client,REPO,true);
        source.values.put(REPO+"/releases/download/pack-v1.0.0/anthub.lock.json","{}".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class,()->ServerProjectPolicy.load(client,REPO,true));
    }
    @Test void otherProjectCannotReuseSnapshot()throws Exception {
        var source=new Source();publish(source,"1.0.0",1);var client=new RepositoryClient(game,source);ServerProjectPolicy.load(client,REPO,true);source.offline=true;
        assertThrows(IllegalStateException.class,()->ServerProjectPolicy.load(client,"https://github.com/example/other",true));
    }
    @Test void verifiesMinimumModVersionAndRequiredFiles()throws Exception {
        var source=new Source();publish(source,"1.0.0",1);var policy=ServerProjectPolicy.load(new RepositoryClient(game,source),REPO,true);
        var state=state(policy);state.addProperty("coreVersion","0.1.0");assertEquals("AntHub: CORE_UPDATE_REQUIRED",policy.verify(state));
        state=state(policy);state.addProperty("requiredFilesDigest","bad");assertEquals("AntHub: REPAIR_REQUIRED",policy.verify(state));
        assertFalse(policy.verify(new JsonObject()).isEmpty());
    }
}
