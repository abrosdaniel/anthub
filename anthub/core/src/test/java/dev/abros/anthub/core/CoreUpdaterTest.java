package dev.abros.anthub.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class CoreUpdaterTest {
    @TempDir Path game;
    static class Source extends Remote {
        final Map<String,byte[]> data=new HashMap<>();
        void put(String url,JsonElement value){data.put(url,Json.GSON.toJson(value).getBytes(StandardCharsets.UTF_8));}
        @Override public byte[] bytes(String url,int limit)throws IOException{
            if(!data.containsKey(url))throw new Remote.Unavailable("offline");return data.get(url);
        }
        @Override public void download(String url,Path to,long limit,AtomicBoolean cancel)throws IOException{Files.write(to,bytes(url,(int)limit));}
    }
    Source source(){
        var remote=new Source();var releases=new JsonArray();
        for(String version:List.of("1.0.0","1.1.0")){var release=new JsonObject();release.addProperty("draft",false);release.addProperty("tag_name","v"+version);releases.add(release);}
        remote.put(CoreUpdater.RELEASES,releases);remote.put(base("1.1.0")+"core.json",descriptor("1.1.0","21.1.250"));return remote;
    }
    String base(String version){return CoreUpdater.REPOSITORY+"/releases/download/v"+version+"/";}
    JsonObject descriptor(String version,String neo){
        var artifact=new JsonObject();artifact.addProperty("minecraft","1.21.1");artifact.addProperty("neoForge",neo);artifact.addProperty("java",21);
        artifact.addProperty("url",base(version)+"anthub-"+version+"-mc1.21.1-neoforge.jar");artifact.addProperty("sha256",Hashes.sha256("new".getBytes()));artifact.addProperty("size",3);artifact.addProperty("helperProtocolVersion",1);
        var list=new JsonArray();list.add(artifact);var result=new JsonObject();result.addProperty("schemaVersion",1);result.addProperty("version",version);result.add("artifacts",list);result.add("protocols",WireProtocols.current());return result;
    }
    @Test void choosesNewCompatibleRelease()throws Exception{
        assertEquals("1.1.0",new CoreUpdater(source()).check("1.0.0","1.21.1","21.1.250").orElseThrow().version());
    }
    @Test void sameVersionAndDowngradeNeverUpdate()throws Exception{
        assertTrue(new CoreUpdater(source()).check("1.1.0","1.21.1","21.1.250").isEmpty());
        assertTrue(new CoreUpdater(source()).check("2.0.0","1.21.1","21.1.250").isEmpty());
    }
    @Test void incompatibleReleaseIsSkipped()throws Exception{
        assertTrue(new CoreUpdater(source()).check("1.0.0","1.21.1","21.1.249").isEmpty());
    }
    @Test void newerCompatibleLoaderPatchCanUpdate()throws Exception{
        assertTrue(new CoreUpdater(source()).check("1.0.0","1.21.1","21.1.999").isPresent());
        assertTrue(new CoreUpdater(source()).check("1.0.0","1.21.1","21.2.0").isEmpty());
        assertTrue(new CoreUpdater(source()).check("1.0.0","1.21.2","21.1.250").isEmpty());
    }
    @Test void stableInstallationSkipsPreviewReleases()throws Exception{
        var remote=source();var list=JsonParser.parseString(new String(remote.data.get(CoreUpdater.RELEASES),StandardCharsets.UTF_8)).getAsJsonArray();
        for(String tag:List.of("v2.0.0-rc.1","v3.0.0")){var preview=new JsonObject();preview.addProperty("tag_name",tag);preview.addProperty("draft",false);preview.addProperty("prerelease",true);list.add(preview);}
        remote.put(CoreUpdater.RELEASES,list);assertEquals("1.1.0",new CoreUpdater(remote).check("1.0.0","1.21.1","21.1.250").orElseThrow().version());
    }
    @Test void foreignUrlAndVersionMismatchRejected(){
        var remote=source();var descriptor=descriptor("1.1.0","21.1.250");descriptor.getAsJsonArray("artifacts").get(0).getAsJsonObject().addProperty("url","https://example.org/mod.jar");remote.put(base("1.1.0")+"core.json",descriptor);
        assertThrows(IOException.class,()->new CoreUpdater(remote).check("1.0.0","1.21.1","21.1.250"));
        descriptor.addProperty("version","9.0.0");remote.put(base("1.1.0")+"core.json",descriptor);
        assertThrows(IOException.class,()->new CoreUpdater(remote).check("1.0.0","1.21.1","21.1.250"));
    }
    @Test void corruptDownloadLeavesInstalledJarUntouched()throws Exception{
        var remote=source();var updater=new CoreUpdater(remote);var update=updater.check("1.0.0","1.21.1","21.1.250").orElseThrow();
        Path jar=game.resolve("mods/anthub.jar");Files.createDirectories(jar.getParent());Files.writeString(jar,"old");remote.data.put(update.artifact().urls().get(0),"bad".getBytes());
        assertThrows(IOException.class,()->updater.stage(game,jar,update,new Cache(game,remote),new JsonObject()));
        assertEquals("old",Files.readString(jar));assertFalse(Files.exists(game.resolve("anthub/pending.json")));
    }
    @Test void validUpdateStagesThenAppliesAndKeepsBackup()throws Exception{
        var remote=source();var updater=new CoreUpdater(remote);var update=updater.check("1.0.0","1.21.1","21.1.250").orElseThrow();
        Path jar=game.resolve("mods/anthub.jar");Files.createDirectories(jar.getParent());Files.writeString(jar,"old");remote.data.put(update.artifact().urls().get(0),"new".getBytes());
        String id=updater.stage(game,jar,update,new Cache(game,remote),new JsonObject());
        assertEquals("old",Files.readString(jar));var transactions=new Transactions(game);transactions.apply(id);
        assertFalse(Files.exists(jar));assertEquals("new",Files.readString(game.resolve("mods/anthub-1.1.0-mc1.21.1-neoforge.jar")));assertFalse(Files.exists(game.resolve("anthub/pending.json")));
        assertEquals("1.1.0",Json.str(Json.read(game.resolve("anthub/state.json")),"coreVersion"));
        try(var paths=Files.walk(transactions.directory(id))){assertTrue(paths.filter(Files::isRegularFile).anyMatch(p->{try{return Files.readString(p).equals("old");}catch(Exception e){return false;}}));}
    }
    @Test void offlineCheckDoesNotCreateTransaction(){
        assertThrows(Remote.Unavailable.class,()->new CoreUpdater(new Source()).check("1.0.0","1.21.1","21.1.250"));
        assertFalse(Files.exists(game.resolve("anthub/pending.json")));
    }
    @Test void breakingReleaseIsMarkedForExplicitReview()throws Exception{
        var remote=source();var descriptor=descriptor("1.1.0","21.1.250");
        descriptor.getAsJsonObject("protocols").addProperty("auth",WireProtocols.version("auth")+1);
        remote.put(base("1.1.0")+"core.json",descriptor);
        assertFalse(new CoreUpdater(remote).check("1.0.0","1.21.1","21.1.250").orElseThrow().preservesProtocols());
        assertTrue(new CoreUpdater(source()).check("1.0.0","1.21.1","21.1.250").orElseThrow().preservesProtocols());
        assertFalse(Files.exists(game.resolve("anthub/pending.json")));
    }
    @Test void missingProtocolMetadataCannotScheduleUpdate(){
        var remote=source();var descriptor=descriptor("1.1.0","21.1.250");descriptor.remove("protocols");
        remote.put(base("1.1.0")+"core.json",descriptor);
        assertThrows(IOException.class,()->new CoreUpdater(remote).check("1.0.0","1.21.1","21.1.250"));
        assertFalse(Files.exists(game.resolve("anthub/pending.json")));
    }
    @Test void versionSelectorCanChooseOlderPublishedReleaseWithoutInstallingIt()throws Exception{
        var remote=source();remote.put(base("1.0.0")+"core.json",descriptor("1.0.0","21.1.250"));
        var releases=new CoreUpdater(remote).releases("1.1.0","1.21.1","21.1.250",20,true);
        assertEquals(List.of("1.0.0"),releases.stream().map(CoreUpdater.Update::version).toList());
        assertTrue(new CoreUpdater(remote).check("1.1.0","1.21.1","21.1.250").isEmpty());
        assertFalse(Files.exists(game.resolve("anthub/pending.json")));
    }

    @Test void brokenReleaseDoesNotHideOtherVersions()throws Exception{
        var remote=source();var list=JsonParser.parseString(new String(remote.data.get(CoreUpdater.RELEASES),StandardCharsets.UTF_8)).getAsJsonArray();
        var bad=new JsonObject();bad.addProperty("draft",false);bad.addProperty("tag_name","v1.2.0");list.add(bad);remote.put(CoreUpdater.RELEASES,list);
        remote.data.put(base("1.2.0")+"core.json","not json".getBytes(StandardCharsets.UTF_8));
        assertEquals("1.1.0",new CoreUpdater(remote).check("1.0.0","1.21.1","21.1.250").orElseThrow().version());
    }
    @Test void metadataRequestsRunConcurrently()throws Exception{
        var original=source();var list=JsonParser.parseString(new String(original.data.get(CoreUpdater.RELEASES),StandardCharsets.UTF_8)).getAsJsonArray();
        var extra=new JsonObject();extra.addProperty("draft",false);extra.addProperty("tag_name","v1.2.0");list.add(extra);original.put(CoreUpdater.RELEASES,list);original.put(base("1.2.0")+"core.json",descriptor("1.2.0","21.1.250"));
        var arrived=new java.util.concurrent.CountDownLatch(2);
        var concurrent=new Remote(){public byte[] bytes(String url,int limit)throws IOException{
            if(url.endsWith("core.json")){arrived.countDown();try{if(!arrived.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Serial metadata requests");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}
            return original.bytes(url,limit);
        }};
        assertEquals(List.of("1.2.0","1.1.0"),new CoreUpdater(concurrent).available("1.0.0","1.21.1","21.1.250",20).stream().map(CoreUpdater.Update::version).toList());
    }
    @Test void updateIndicatorOnlyFetchesSmallBatch()throws Exception{
        var original=source();var releases=new JsonArray();
        for(int i=1;i<=40;i++){var entry=new JsonObject();entry.addProperty("draft",false);entry.addProperty("tag_name","v1.1."+i);releases.add(entry);original.put(base("1.1."+i)+"core.json",descriptor("1.1."+i,"21.1.250"));}
        original.put(CoreUpdater.RELEASES,releases);var count=new java.util.concurrent.atomic.AtomicInteger();
        var remote=new Remote(){public byte[] bytes(String url,int limit)throws IOException{if(url.endsWith("core.json"))count.incrementAndGet();return original.bytes(url,limit);}};
        assertEquals("1.1.40",new CoreUpdater(remote).check("1.0.0","1.21.1","21.1.250").orElseThrow().version());assertEquals(4,count.get());
    }

}
