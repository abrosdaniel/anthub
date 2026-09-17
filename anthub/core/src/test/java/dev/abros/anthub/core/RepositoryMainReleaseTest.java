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
  var remote=new FixtureRemote();remote.values.put("https://api.github.com/repos/example/project/releases?per_page=100&page=1",("[{\"tag_name\":\"pack-v1.0.0\",\"draft\":false,\"prerelease\":false}]").getBytes(StandardCharsets.UTF_8));remote.values.put(url,bytes);remote.values.put(url.replace(".json",".sha256"),Hashes.sha256(bytes).getBytes(StandardCharsets.UTF_8));return remote;
 }
 @Test void opensWithoutKeysAndReusesTrustedOfflineSnapshot()throws Exception{
  var remote=source(fixture());var client=new RepositoryClient(game,remote);var release=client.fetchOrCached("https://github.com/example/project");assertFalse(release.trusted());assertEquals(3,remote.requested.size());assertTrue(remote.requested.stream().noneMatch(url->url.contains("keys/")||url.contains("sig.json")));
  client.trust(release);remote.offline=true;assertTrue(client.fetchOrCached("https://github.com/example/project").offline());
 }
 @Test void rejectsTamperedLockInsteadOfUsingCache()throws Exception{
  var remote=source(fixture());var client=new RepositoryClient(game,remote);client.trust(client.fetch("https://github.com/example/project"));String url=remote.values.keySet().stream().filter(s->s.endsWith("anthub.lock.json")).findFirst().orElseThrow();remote.values.put(url,"{}".getBytes(StandardCharsets.UTF_8));assertThrows(java.io.IOException.class,()->client.fetchOrCached("https://github.com/example/project"));
 }
 @Test void draftOnlyIsNotAnAvailablePack()throws Exception{var remote=source(fixture());String url="https://api.github.com/repos/example/project/releases?per_page=100&page=1";remote.values.put(url,"[{\"tag_name\":\"pack-v1.0.0\",\"draft\":true,\"prerelease\":false}]".getBytes(StandardCharsets.UTF_8));assertThrows(java.io.IOException.class,()->new RepositoryClient(game,remote).fetch("https://github.com/example/project"));}

 static final String API="https://api.github.com/repos/example/project/releases?per_page=100&page=";
 static final String REPO="https://github.com/example/project";
 JsonObject entry(String version){var j=new JsonObject();j.addProperty("tag_name","pack-v"+version);j.addProperty("draft",false);j.addProperty("prerelease",false);return j;}
 void asset(FixtureRemote remote,String version)throws Exception{
  var j=Json.parse(new String(fixture(),StandardCharsets.UTF_8));j.getAsJsonObject("release").addProperty("version",version);
  byte[] bytes=Json.GSON.toJson(j).getBytes(StandardCharsets.UTF_8);String base=REPO+"/releases/download/pack-v"+version+"/anthub.lock.";
  remote.values.put(base+"json",bytes);remote.values.put(base+"sha256",Hashes.sha256(bytes).getBytes(StandardCharsets.UTF_8));
 }
 @Test void choosesHighestNumericVersionAcrossPages()throws Exception{
  var remote=source(fixture());var first=new com.google.gson.JsonArray();for(int n=0;n<100;n++)first.add(entry("1.9.0"));
  remote.values.put(API+1,Json.GSON.toJson(first).getBytes(StandardCharsets.UTF_8));var second=new com.google.gson.JsonArray();second.add(entry("1.10.0"));
  var preview=entry("9.0.0");preview.addProperty("prerelease",true);second.add(preview);var draft=entry("8.0.0");draft.addProperty("draft",true);second.add(draft);
  remote.values.put(API+2,Json.GSON.toJson(second).getBytes(StandardCharsets.UTF_8));asset(remote,"1.10.0");
  assertEquals("1.10.0",new RepositoryClient(game,remote).fetch(REPO).manifest().version());
 }
 @Test void rejectsRollbackAndMutationEvenWithUpdatedChecksum()throws Exception{
  var remote=source(fixture());var client=new RepositoryClient(game,remote);client.trust(client.fetch(REPO));
  String base=REPO+"/releases/download/pack-v1.0.0/anthub.lock.";var j=Json.parse(new String(remote.values.get(base+"json"),StandardCharsets.UTF_8));j.getAsJsonObject("project").addProperty("name","Changed");
  byte[] changed=Json.GSON.toJson(j).getBytes(StandardCharsets.UTF_8);remote.values.put(base+"json",changed);remote.values.put(base+"sha256",Hashes.sha256(changed).getBytes(StandardCharsets.UTF_8));
  assertThrows(java.io.IOException.class,()->client.fetchOrCached(REPO));
  asset(remote,"0.9.0");remote.values.put(API+1,("["+entry("0.9.0")+"]").getBytes(StandardCharsets.UTF_8));
  assertThrows(java.io.IOException.class,()->client.fetchOrCached(REPO));assertEquals("1.0.0",client.cached(REPO).manifest().version());
 }
 @Test void missingNewReleaseDoesNotSilentlyUseOldCache()throws Exception{
  var remote=source(fixture());var client=new RepositoryClient(game,remote);client.trust(client.fetch(REPO));
  remote.values.put(API+1,("["+entry("2.0.0")+"]").getBytes(StandardCharsets.UTF_8));
  assertThrows(java.io.IOException.class,()->client.fetchOrCached(REPO));
 }
 @Test void missingHttpAssetDoesNotFallBack()throws Exception{
  var initial=source(fixture());var client=new RepositoryClient(game,initial);client.trust(client.fetch(REPO));
  var remote=new Remote(){public byte[] bytes(String url,int limit)throws java.io.IOException{if(url.contains("api.github.com"))return initial.values.get(API+1);throw new HttpFailure(404,java.net.URI.create(url));}};
  assertThrows(java.io.IOException.class,()->new RepositoryClient(game,remote).fetchOrCached(REPO));
 }
}
