package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.JsonObject;
import static org.junit.jupiter.api.Assertions.*;
class RepositoryMainReleaseTest {
 @TempDir Path game;
 byte[] fixture()throws Exception{try(var input=getClass().getResourceAsStream("/fixtures/lock.json")){var j=Json.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));return Json.GSON.toJson(j).getBytes(StandardCharsets.UTF_8);}}
 class FixtureRemote extends Remote {
  Map<String,byte[]> values=new HashMap<>();List<String> requested=new ArrayList<>();boolean offline;
  public byte[] bytes(String url,int limit)throws java.io.IOException{requested.add(url);if(offline)throw new Unavailable("offline");if(!values.containsKey(url))throw new java.io.IOException("Unexpected request: "+url);return values.get(url);}
 }
 FixtureRemote source(byte[] bytes)throws Exception{var lock=Json.parse(new String(bytes,StandardCharsets.UTF_8));var release=lock.getAsJsonObject("release");String repo=Json.str(lock.getAsJsonObject("project"),"repository");String url=repo+"/releases/download/pack-v1.0.0/anthub.lock.json";
  var pointer=new JsonObject();pointer.addProperty("schemaVersion",1);pointer.addProperty("repository",repo);pointer.addProperty("channel","stable");pointer.addProperty("version",Json.str(release,"version"));pointer.add("sequence",release.get("sequence"));pointer.addProperty("lockUrl",url);pointer.addProperty("lockSha256",Hashes.sha256(bytes));pointer.addProperty("publishedAt","2026-09-15T00:00:00Z");
  var remote=new FixtureRemote();remote.values.put(Repositories.raw(repo,"channels/stable.json"),Json.GSON.toJson(pointer).getBytes(StandardCharsets.UTF_8));remote.values.put(url,bytes);return remote;
 }
 @Test void opensWithoutKeysAndReusesTrustedOfflineSnapshot()throws Exception{
  var remote=source(fixture());var client=new RepositoryClient(game,remote);var release=client.fetchOrCached("https://github.com/example/project");assertFalse(release.trusted());assertEquals(2,remote.requested.size());assertTrue(remote.requested.stream().noneMatch(url->url.contains("keys/")||url.contains("sig.json")));
  client.trust(release);remote.offline=true;assertTrue(client.fetchOrCached("https://github.com/example/project").offline());
 }
 @Test void rejectsTamperedLockInsteadOfUsingCache()throws Exception{
  var remote=source(fixture());var client=new RepositoryClient(game,remote);client.trust(client.fetch("https://github.com/example/project"));String url=remote.values.keySet().stream().filter(s->s.endsWith("anthub.lock.json")).findFirst().orElseThrow();remote.values.put(url,"{}".getBytes(StandardCharsets.UTF_8));assertThrows(java.io.IOException.class,()->client.fetchOrCached("https://github.com/example/project"));
 }
 @Test void removedChannelsAreRejected()throws Exception{var remote=source(fixture());String url=Repositories.raw("https://github.com/example/project","channels/stable.json");var pointer=Json.parse(new String(remote.values.get(url),StandardCharsets.UTF_8));pointer.addProperty("channel","beta");remote.values.put(url,Json.GSON.toJson(pointer).getBytes(StandardCharsets.UTF_8));var client=new RepositoryClient(game,remote);assertThrows(IllegalArgumentException.class,()->client.fetch("https://github.com/example/project"));}
}
