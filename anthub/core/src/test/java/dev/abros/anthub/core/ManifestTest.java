package dev.abros.anthub.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
class ManifestTest {
    @TempDir Path game;
    byte[] resource(String name)throws Exception{try(var in=getClass().getResourceAsStream("/fixtures/"+name)){return in.readAllBytes();}}
    JsonObject fixture()throws Exception{var j=Json.parse(new String(resource("lock.json"),java.nio.charset.StandardCharsets.UTF_8));return j;}
    @Test void sourceAndRuntimeContractAgree()throws Exception{var m=Manifest.parse(fixture());assertEquals(1,m.servers().size());assertEquals("1.21.1",m.minecraft());assertEquals(Set.of("example-config"),new Selection(m).initial());}
    @Test void foreignFileIsNeverSilentlyAdopted()throws Exception{
        var m=Manifest.parse(fixture());var f=m.files().getFirst();Path p=SafePaths.resolve(game,f.path());Files.createDirectories(p.getParent());Files.writeString(p,"user=true");var plan=Planner.plan(game,m,Set.of(),Map.of(),new Cache(game,new Remote()));assertFalse(plan.conflicts().isEmpty());assertTrue(plan.changes().isEmpty());
    }
    @Test void repairRequiresBackupConsentForModifiedEnforcedFile()throws Exception{
        var j=fixture();var file=j.getAsJsonArray("files").get(0).getAsJsonObject();file.addProperty("policy","enforce");var m=Manifest.parse(j);var f=m.files().getFirst();Path p=SafePaths.resolve(game,f.path());Files.createDirectories(p.getParent());Files.writeString(p,"user=true");var old=Map.of(f.path(),new Planner.Owned(f.sha256(),"enforce",f.componentId()));var cache=new Cache(game,new Remote());assertFalse(Planner.plan(game,m,Set.of(),old,cache).conflicts().isEmpty());var accepted=Planner.plan(game,m,Set.of(),old,cache,true);assertTrue(accepted.conflicts().isEmpty());assertEquals(Hashes.sha256(p),accepted.changes().getFirst().before());
    }
    @Test void versionsRespectPrerelease(){assertTrue(Versions.compare("1.0.0","1.0.0-alpha.1")>0);assertTrue(Versions.compare("1.0.0-alpha.10","1.0.0-alpha.2")>0);assertEquals(0,Versions.compare("1.0.0+build1","1.0.0+build2"));}
    @Test void anthubCompatibilityUsesMajorOnly(){assertTrue(Versions.sameMajor("1.0.0","1.9.8"));assertTrue(Versions.sameMajor("1.9.8","1.0.0"));assertFalse(Versions.sameMajor("2.0.0","1.9.8"));assertFalse(Versions.sameMajor("1.0.0-beta","1.0.0"));assertFalse(Versions.sameMajor("1.0","1.0.0"));}
    @Test void projectBranchAcceptsOnlyMatchingReleaseLine()throws Exception{
        assertTrue(Versions.supportsBranch("1.0.0","1.x"));
        assertTrue(Versions.supportsBranch("1.99.88","1.x"));
        assertFalse(Versions.supportsBranch("2.0.0","1.x"));
        for(String branch:List.of("1.0.0","1.*","01.x","1.x.x","1.X","")){
            assertFalse(Versions.supportsBranch("1.0.0",branch));
            var json=fixture();json.getAsJsonObject("anthub").addProperty("version",branch);
            assertThrows(Exception.class,()->Manifest.parse(json));
        }
        assertFalse(Versions.supportsBranch("1.0.0-beta","1.x"));
        assertEquals("",new Hub(game,"1.9.8","21.1.250").incompatibility(Manifest.parse(fixture())));
        assertEquals("AntHub 1.x",new Hub(game,"2.0.0","21.1.250").incompatibility(Manifest.parse(fixture())));
    }
    @Test void duplicateCasePathRejected()throws Exception{var j=fixture();var fs=j.getAsJsonArray("files");var copy=fs.get(0).deepCopy().getAsJsonObject();copy.addProperty("path","config/EXAMPLE-PROJECT.toml");fs.add(copy);assertThrows(Exception.class,()->Manifest.parse(j));}

    @Test void approvedReplacementIsBackedUpAndUnlistedFilesStayUntouched()throws Exception{
        var json=fixture();var entry=json.getAsJsonArray("files").get(0).getAsJsonObject();byte[] replacement="project=true".getBytes();entry.addProperty("sha256",Hashes.sha256(replacement));entry.addProperty("size",replacement.length);var manifest=Manifest.parse(json);var file=manifest.files().getFirst();Path path=SafePaths.resolve(game,file.path());Files.createDirectories(path.getParent());Files.writeString(path,"personal=true");Path other=game.resolve("config/unrelated.toml");Files.writeString(other,"untouched");
        var cache=new Cache(game,new Remote());Path object=cache.path(file.sha256());Files.createDirectories(object.getParent());Files.write(object,replacement);
        var blocked=Planner.plan(game,manifest,Set.of(),Map.of(),cache,true,Set.of("config/unrelated.toml"));assertFalse(blocked.conflicts().isEmpty());
        var approved=Planner.plan(game,manifest,Set.of(),Map.of(),cache,true,Set.of(file.path()));assertTrue(approved.conflicts().isEmpty());assertEquals("personal=true",Files.readString(path));
        var tx=new Transactions(game);tx.prepare(approved,new byte[0],new JsonObject());tx.apply(approved.id());assertEquals("project=true",Files.readString(path));assertEquals("personal=true",Files.readString(tx.directory(approved.id()).resolve("preimages/0")));assertEquals("untouched",Files.readString(other));
    }
    @Test void findingProjectPersistsSelectionWithoutActivatingIt()throws Exception{
        var manifest=Manifest.parse(fixture());var hub=new Hub(game,"1.0.0","21.1.250");hub.rememberProject(manifest);var restarted=new Hub(game,"1.0.0","21.1.250");assertTrue(restarted.saved().contains(manifest.repository()));assertEquals(manifest.repository(),restarted.selectedRepository());assertEquals(manifest.name(),restarted.menuProjectName());assertNull(restarted.active());
    }

    @Test void unchangedFilesActivateAndUpdateWithoutRestart()throws Exception{
        var json=fixture();byte[] content="same configuration".getBytes();var entry=json.getAsJsonArray("files").get(0).getAsJsonObject();entry.addProperty("sha256",Hashes.sha256(content));entry.addProperty("size",content.length);entry.addProperty("policy","enforce");
        var manifest=Manifest.parse(json);var file=manifest.files().getFirst();Path path=SafePaths.resolve(game,file.path());Files.createDirectories(path.getParent());Files.write(path,content);
        var hub=new Hub(game,"1.0.0","21.1.250");var release=new RepositoryClient.Release(manifest,Json.GSON.toJson(json) .getBytes(),false,false,"");
        var plan=hub.plan(release,Set.of(),Set.of(file.path()));assertTrue(plan.changes().isEmpty());assertTrue(plan.conflicts().isEmpty());
        assertEquals("",hub.stage(release,plan,new java.util.concurrent.atomic.AtomicBoolean(),s->{}));assertFalse(Files.exists(game.resolve("anthub/pending.json")));assertEquals(manifest.version(),hub.active().version());
        json.getAsJsonObject("release").addProperty("version","1.0.1");var updated=Manifest.parse(json);var next=new RepositoryClient.Release(updated,Json.GSON.toJson(json).getBytes(),true,false,"");
        assertEquals("",hub.stage(next,hub.plan(next,Set.of()),new java.util.concurrent.atomic.AtomicBoolean(),s->{}));assertEquals("1.0.1",new Hub(game,"1.0.0","21.1.250").active().version());assertFalse(Files.exists(game.resolve("anthub/pending.json")));assertArrayEquals(content,Files.readAllBytes(path));
    }


    @Test void cachedReplacementStillChangesGameFiles()throws Exception{
        var json=fixture();byte[] content="replacement".getBytes();var entry=json.getAsJsonArray("files").get(0).getAsJsonObject();entry.addProperty("sha256",Hashes.sha256(content));entry.addProperty("size",content.length);entry.addProperty("policy","enforce");var manifest=Manifest.parse(json);var file=manifest.files().getFirst();Path path=SafePaths.resolve(game,file.path());Files.createDirectories(path.getParent());Files.writeString(path,"old");var cache=new Cache(game,new Remote());Path object=cache.path(file.sha256());Files.createDirectories(object.getParent());Files.write(object,content);
        var plan=Planner.plan(game,manifest,Set.of(),Map.of(file.path(),new Planner.Owned(Hashes.sha256(path),"enforce",file.componentId())),cache);
        assertEquals(0,plan.downloadBytes());assertEquals(1,plan.changes().size());assertTrue(plan.conflicts().isEmpty());
    }

    @Test void optionalComponentsStartSelectedAndCanBeUnchecked()throws Exception{
        var json=fixture();var optional=json.getAsJsonArray("components").get(0).deepCopy().getAsJsonObject();optional.addProperty("id","optional-mod");optional.addProperty("kind","optional");json.getAsJsonArray("components").add(optional);var selection=new Selection(Manifest.parse(json));
        assertTrue(selection.initial().contains("optional-mod"));assertFalse(selection.resolve(Set.of()).contains("optional-mod"));assertTrue(selection.resolve(Set.of()).contains("example-config"));
    }

    @Test void removalWaitsUntilProjectIsDeactivated()throws Exception{
        var manifest=Manifest.parse(fixture());var hub=new Hub(game,"1.0.0","21.1.250");hub.rememberProject(manifest);var state=new JsonObject();state.add("lock",manifest.json());Json.write(game.resolve("anthub/state.json"),state);hub.removeAfterDeactivation(manifest.repository());
        assertTrue(new Hub(game,"1.0.0","21.1.250").saved().contains(manifest.repository()));
        Files.writeString(game.resolve("personal.txt"),"keep");Json.write(game.resolve("anthub/state.json"),new JsonObject());var reopened=new Hub(game,"1.0.0","21.1.250");assertFalse(reopened.saved().contains(manifest.repository()));assertEquals("keep",Files.readString(game.resolve("personal.txt")));
    }
}
