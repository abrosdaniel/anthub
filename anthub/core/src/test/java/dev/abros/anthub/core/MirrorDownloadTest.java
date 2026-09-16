package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class MirrorDownloadTest {
 @TempDir Path game;
 Manifest.FileEntry file(){return new Manifest.FileEntry("mod","mods/test.jar","1",List.of("https://first.example/mod.jar","https://second.example/mod.jar"),Hashes.sha256("good".getBytes()),4,"enforce");}
 @Test void mismatchFallsBackAndVerifiedCacheAvoidsNetwork()throws Exception{
  var calls=new ArrayList<String>();var remote=new Remote(){@Override public void download(String url,Path to,long limit,AtomicBoolean cancel)throws IOException{calls.add(url);assertFalse(Files.exists(to));Files.writeString(to,url.contains("first")?"evil":"good");}};
  var cache=new Cache(game,remote);assertEquals("good",Files.readString(cache.obtain(file(),new AtomicBoolean())));cache.obtain(file(),new AtomicBoolean());assertEquals(file().urls(),calls);
 }
 @Test void interruptedSourceStartsNextMirrorCleanly()throws Exception{
  var remote=new Remote(){@Override public void download(String url,Path to,long limit,AtomicBoolean cancel)throws IOException{assertFalse(Files.exists(to));if(url.contains("first")){Files.writeString(to,"partial");throw new IOException("offline");}Files.writeString(to,"good");}};
  assertEquals("good",Files.readString(new Cache(game,remote).obtain(file(),new AtomicBoolean())));
 }
 @Test void cancellationDoesNotTryAnotherMirror()throws Exception{
  var calls=new ArrayList<String>();var remote=new Remote(){@Override public void download(String url,Path to,long limit,AtomicBoolean cancel)throws IOException{calls.add(url);cancel.set(true);throw new IOException("Cancelled");}};
  assertThrows(IOException.class,()->new Cache(game,remote).obtain(file(),new AtomicBoolean()));assertEquals(1,calls.size());
 }
 @Test void allFailedSourcesNeverEnterCache()throws Exception{
  var remote=new Remote(){@Override public void download(String url,Path to,long limit,AtomicBoolean cancel)throws IOException{throw new IOException("offline");}};var cache=new Cache(game,remote);
  var error=assertThrows(IOException.class,()->cache.obtain(file(),new AtomicBoolean()));assertEquals(2,error.getSuppressed().length);assertFalse(cache.contains(file().sha256()));
 }
}
