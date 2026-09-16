package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class InstallUxTest {
 @TempDir Path game;
 RepositoryClient.Release release()throws Exception{
  var json=Json.parse(new String(getClass().getResourceAsStream("/fixtures/lock.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
  var files=json.getAsJsonArray("files");while(files.size()>1)files.remove(files.size()-1);var file=files.get(0).getAsJsonObject();file.addProperty("path","config/example.toml");file.addProperty("policy","preserve");file.addProperty("sha256",Hashes.sha256("project".getBytes()));file.addProperty("size",7);
  return new RepositoryClient.Release(Manifest.parse(json),Json.GSON.toJson(json).getBytes(),false,false,"");
 }
 Path local()throws Exception{Path p=game.resolve("config/example.toml");Files.createDirectories(p.getParent());Files.writeString(p,"mine");return p;}
 @Test void keepConfigIsDefaultAndDoesNotRequestRestart()throws Exception{
  var file=local();var hub=new Hub(game,"1.0.0-alpha.1","21.1.250");var ops=new PackOperations(hub,Runnable::run);var review=ops.review(release(),Set.of(),new AtomicBoolean()).join();assertEquals(Set.of("config/example.toml"),review.kept());assertTrue(review.plan().changes().isEmpty());assertEquals("",ops.install(review,new AtomicBoolean(),s->{}).join());assertEquals("mine",Files.readString(file));
 }
 @Test void replacementBacksUpOriginalAndHistoryRecordsCommit()throws Exception{
  local();var hub=new Hub(game,"1.0.0-alpha.1","21.1.250");var r=release();var ops=new PackOperations(hub,Runnable::run);var review=ops.review(r,Set.of(),Map.of("config/example.toml",false),new AtomicBoolean()).join();assertEquals(1,review.plan().changes().size());
  Path cached=hub.cache.path(Hashes.sha256("project".getBytes()));Files.createDirectories(cached.getParent());Files.writeString(cached,"project");var next=new com.google.gson.JsonObject();next.add("lock",r.manifest().json());var tx=new Transactions(game);tx.prepare(review.plan(),r.bytes(),next);tx.apply(review.plan().id());
  assertEquals("mine",Files.readString(tx.directory(review.plan().id()).resolve("preimages/0")));assertEquals("project",Files.readString(game.resolve("config/example.toml")));var entries=InstallationHistory.read(game,r.manifest().repository());assertEquals(1,entries.size());assertEquals("COMMITTED",entries.getFirst().status());assertFalse(entries.getFirst().at().isEmpty());
 }
 @Test void changingKeptConfigAfterReviewRequiresNewConsent()throws Exception{
  Path file=local();var ops=new PackOperations(new Hub(game,"1.0.0-alpha.1","21.1.250"),Runnable::run);var review=ops.review(release(),Set.of(),new AtomicBoolean()).join();Files.writeString(file,"new local edits");assertThrows(CompletionException.class,()->ops.install(review,new AtomicBoolean(),s->{}).join());
 }
 @Test void diffIsBoundedAndRejectsBinary(){assertTrue(ConfigDiff.compare("mine","project").contains("- mine\n+ project"));assertEquals("",ConfigDiff.compare("same","same"));assertThrows(IllegalArgumentException.class,()->ConfigDiff.compare("\0","text"));assertTrue(ConfigDiff.compare("x\n".repeat(1000),"y\n".repeat(1000)).length()<10000);}
 @Test void diagnosticsRemovesCredentialsAndPrivatePaths(){String text=Diagnostics.redact("token=secret\nhttps://user:password@host/path?key=private\n"+game+"/mods/file.jar\n/home/alice/private.log\nC:\\Users\\Alice\\private.log\n192.0.2.15",game);for(String forbidden:List.of("secret","password@","alice","Alice","192.0.2.15",game.toString()))assertFalse(text.contains(forbidden),forbidden);}
 @Test void progressCountsVerifiedFilesAndReportsMirror(){var messages=new ArrayList<String>();var progress=new DownloadProgress(10,messages::add);progress.source("mods/a.jar",1);progress.bytes("mods/a.jar",10,4);progress.source("mods/a.jar",2);progress.bytes("mods/a.jar",10,10);progress.verified("mods/a.jar",10);assertTrue(messages.getLast().startsWith("DOWNLOAD|10|10|"));assertTrue(messages.getLast().endsWith("|2|mods/a.jar"));}
}
