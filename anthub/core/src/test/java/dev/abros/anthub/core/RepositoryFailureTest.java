package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;
class RepositoryFailureTest {
 @TempDir Path game;
 @Test void firstInstall404KeepsOriginalCause()throws Exception{
  var failure=new Remote.HttpFailure(404,URI.create("https://raw.githubusercontent.com/owner/repo/HEAD/keys/project.pub.json"));
  var client=new RepositoryClient(game,new Remote(){@Override public byte[] bytes(String url,int limit)throws java.io.IOException{throw failure;}});
  assertSame(failure,assertThrows(Remote.HttpFailure.class,()->client.fetchOrCached("https://github.com/owner/repo")));
  assertFalse(Files.exists(game.resolve("mods")));
 }
 @Test void firstInstallOfflineKeepsNetworkFailure()throws Exception{
  var failure=new Remote.Unavailable("offline");var client=new RepositoryClient(game,new Remote(){@Override public byte[] bytes(String url,int limit)throws java.io.IOException{throw failure;}});
  assertSame(failure,assertThrows(Remote.Unavailable.class,()->client.fetchOrCached("https://github.com/owner/repo")));
 }
}
