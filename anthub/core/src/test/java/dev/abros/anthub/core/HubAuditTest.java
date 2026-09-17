package dev.abros.anthub.core;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class HubAuditTest {
 @TempDir Path game;
 private Hub installed(boolean dependency)throws Exception {
  JsonObject lock;try(var input=getClass().getResourceAsStream("/fixtures/lock.json")){lock=Json.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
  var base=lock.getAsJsonArray("components").get(0).getAsJsonObject();base.addProperty("id","required");
  var optional=base.deepCopy();optional.addProperty("id","optional");optional.addProperty("kind","optional");lock.getAsJsonArray("components").add(optional);
  if(dependency)base.getAsJsonArray("dependencies").add("optional");
  var file=lock.getAsJsonArray("files").get(0).getAsJsonObject();file.addProperty("componentId","required");file.addProperty("policy","enforce");file.addProperty("path","mods/required.jar");file.addProperty("sha256",Hashes.sha256("ok".getBytes()));file.addProperty("size",2);
  var other=file.deepCopy();other.addProperty("componentId","optional");other.addProperty("path","mods/optional.jar");lock.getAsJsonArray("files").add(other);
  Files.createDirectories(game.resolve("mods"));Files.writeString(game.resolve("mods/required.jar"),"ok");Files.writeString(game.resolve("mods/optional.jar"),"ok");
  var state=new JsonObject();state.add("lock",lock);var ownership=new JsonObject();for(String path:List.of("mods/required.jar","mods/optional.jar")){var owned=new JsonObject();owned.addProperty("hash",Hashes.sha256("ok".getBytes()));owned.addProperty("policy","enforce");ownership.add(path,owned);}state.add("ownership",ownership);Json.write(game.resolve("anthub/state.json"),state);return new Hub(game,"1.0.0","21.1.250");
 }
 @Test void missingOptionalDoesNotBlockButRequiredDoes()throws Exception {var hub=installed(false);Files.delete(game.resolve("mods/optional.jar"));assertTrue(hub.audit().isEmpty());assertEquals(PackProof.digest(hub.active(),null),PackProof.digest(hub.active(),game));Files.delete(game.resolve("mods/required.jar"));assertEquals(List.of("mods/required.jar"),hub.audit());assertNotEquals(PackProof.digest(hub.active(),null),PackProof.digest(hub.active(),game));}
 @Test void dependencyOfRequiredRemainsRequired()throws Exception {var hub=installed(true);Files.delete(game.resolve("mods/optional.jar"));assertEquals(List.of("mods/optional.jar"),hub.audit());}
 @Test void modifiedOptionalStillGetsChecked()throws Exception {var hub=installed(false);Files.writeString(game.resolve("mods/optional.jar"),"changed");assertEquals(List.of("mods/optional.jar"),hub.audit());}
 @Test void cleanupWithoutObjectsIsSafe()throws Exception {assertEquals(0,new Hub(game,"1.0.0","21.1.250").clearUnusedCache());}
 @Test void cleanupKeepsPendingAndReleasesCommittedObjects()throws Exception {var hub=new Hub(game,"1.0.0","21.1.250");String hash=Hashes.sha256("new".getBytes());Path object=hub.cache.path(hash);Files.createDirectories(object.getParent());Files.writeString(object,"new");var plan=new Planner.Plan(UUID.randomUUID().toString(),"project",List.of(new Planner.Change("mods/a.jar",null,hash)),Map.of(),Set.of(),List.of(),0);var tx=new Transactions(game);tx.prepare(plan,new byte[0],new JsonObject());assertEquals(0,hub.clearUnusedCache());tx.abortReady(plan.id());assertEquals(3,hub.clearUnusedCache());assertFalse(Files.exists(object));}
}
