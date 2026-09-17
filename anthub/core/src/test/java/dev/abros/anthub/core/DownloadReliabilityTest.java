package dev.abros.anthub.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.SSLSession;
import static org.junit.jupiter.api.Assertions.*;

class DownloadReliabilityTest {
 @TempDir Path game;
 static final String URL="https://1.1.1.1/mod.jar";
 Manifest.FileEntry file(){return new Manifest.FileEntry("mod","mods/test.jar","1",List.of(URL),Hashes.sha256("good".getBytes()),4,"enforce");}
 static HttpResponse<InputStream> response(HttpRequest request,int status,Map<String,List<String>> headers,InputStream body){
  return new HttpResponse<>(){
   public int statusCode(){return status;}public HttpRequest request(){return request;}
   public Optional<HttpResponse<InputStream>> previousResponse(){return Optional.empty();}
   public HttpHeaders headers(){return HttpHeaders.of(headers,(a,b)->true);}public InputStream body(){return body;}
   public Optional<SSLSession> sslSession(){return Optional.empty();}public URI uri(){return request.uri();}
   public HttpClient.Version version(){return HttpClient.Version.HTTP_1_1;}
  };
 }
 @Test void permanentFailureIsNotRetried()throws Exception{
  var calls=new AtomicInteger();var remote=new Remote(){@Override HttpResponse<InputStream> send(HttpRequest r){calls.incrementAndGet();return response(r,404,Map.of(),InputStream.nullInputStream());}};
  assertThrows(Remote.HttpFailure.class,()->remote.download(URL,game.resolve("part"),4,new AtomicBoolean()));assertEquals(1,calls.get());
 }
 @Test void interruptedBodyResumesAndReportsAbsolutePosition()throws Exception{
  var calls=new AtomicInteger();var positions=new ArrayList<Long>();var bytes=new AtomicLong();
  var remote=new Remote(){@Override HttpResponse<InputStream> send(HttpRequest r){
   if(calls.incrementAndGet()==1)return response(r,200,Map.of("ETag",List.of("\"v1\"")),new ByteArrayInputStream("go".getBytes()));
   assertEquals("bytes=2-",r.headers().firstValue("Range").orElseThrow());assertEquals("\"v1\"",r.headers().firstValue("If-Range").orElseThrow());
   return response(r,206,Map.of("ETag",List.of("\"v1\""),"Content-Range",List.of("bytes 2-3/4")),new ByteArrayInputStream("od".getBytes()));
  }};
  Path p=game.resolve("part");remote.download(URL,p,4,new AtomicBoolean(),(position,received)->{positions.add(position);bytes.addAndGet(received);});
  assertEquals("good",Files.readString(p));assertEquals(List.of(0L,2L,2L,4L),positions);assertEquals(4,bytes.get());
 }
 @Test void rangeIgnoredResetsProgressAndDoesNotAppend()throws Exception{
  Path p=game.resolve("part");Files.writeString(p,"go");Files.writeString(game.resolve("part.etag"),"\"old\"");var positions=new ArrayList<Long>();
  var remote=new Remote(){@Override HttpResponse<InputStream> send(HttpRequest r){return response(r,200,Map.of("ETag",List.of("\"new\"")),new ByteArrayInputStream("good".getBytes()));}};
  remote.download(URL,p,4,new AtomicBoolean(),(position,received)->positions.add(position));assertEquals("good",Files.readString(p));assertEquals(List.of(0L,4L),positions);
 }
 @Test void cancelledDownloadSurvivesNewCacheInstance()throws Exception{
  var cancel=new AtomicBoolean();var calls=new AtomicInteger();
  var remote=new Remote(){@Override public void download(String url,Path to,long size,AtomicBoolean c)throws IOException{
   if(calls.incrementAndGet()==1){Files.writeString(to,"go");Files.writeString(to.resolveSibling(to.getFileName()+".etag"),"\"v1\"");c.set(true);throw new IOException("Cancelled");}
   assertEquals("go",Files.readString(to));Files.writeString(to,"good");
  }};
  assertThrows(IOException.class,()->new Cache(game,remote).obtain(file(),cancel));assertEquals("good",Files.readString(new Cache(game,remote).obtain(file(),new AtomicBoolean())));
 }
 @Test void concurrentProjectsShareOneTransferAndWaiterCanCancel()throws Exception{
  var calls=new AtomicInteger();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  var remote=new Remote(){@Override public void download(String url,Path to,long size,AtomicBoolean c)throws IOException,InterruptedException{calls.incrementAndGet();entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));Files.writeString(to,"good");}};
  try(var pool=Executors.newFixedThreadPool(3)){
   var first=pool.submit(()->new Cache(game,remote).obtain(file(),new AtomicBoolean()));assertTrue(entered.await(2,TimeUnit.SECONDS));
   var cancelled=new AtomicBoolean(true);var waiter=pool.submit(()->new Cache(game,remote).obtain(file(),cancelled));assertThrows(ExecutionException.class,()->waiter.get(2,TimeUnit.SECONDS));
   var second=pool.submit(()->new Cache(game,remote).obtain(file(),new AtomicBoolean()));release.countDown();assertEquals(first.get(3,TimeUnit.SECONDS),second.get(3,TimeUnit.SECONDS));assertEquals(1,calls.get());
  }finally{release.countDown();}
 }
 @Test void repeatedBytesDoNotCompleteProgressBeforeVerification(){
  var messages=new ArrayList<String>();var meter=new DownloadProgress(10,messages::add);meter.source("x",1);meter.position("x",10,8,8);meter.position("x",10,0,0);meter.position("x",10,4,4);meter.source("y",1);
  assertTrue(messages.getLast().startsWith("DOWNLOAD|4|10|"));meter.position("x",10,10,6);meter.source("y",1);assertTrue(messages.getLast().startsWith("DOWNLOAD|9|10|"));meter.verified("x",10);assertTrue(messages.getLast().startsWith("DOWNLOAD|10|10|"));
 }
 @Test void retryPolicyAndDateHeaders(){assertFalse(Remote.retryable(403));assertFalse(Remote.retryable(404));assertTrue(Remote.retryable(429));assertTrue(Remote.retryable(502));assertEquals(60,Remote.retryDelay("999"));assertEquals(0,Remote.retryDelay("-2"));assertEquals(0,Remote.retryDelay("Wed, 21 Oct 2015 07:28:00 GMT"));}
 @Test void hostCooldownIsSharedButDoesNotBlockOtherHosts()throws Exception{
  var firstCancel=new AtomicBoolean();var requests=new AtomicInteger();
  var remote=new Remote(){@Override HttpResponse<InputStream> send(HttpRequest r){
   requests.incrementAndGet();if(r.uri().getHost().equals("9.9.9.9")){firstCancel.set(true);return response(r,429,Map.of("Retry-After",List.of("60")),InputStream.nullInputStream());}
   return response(r,200,Map.of(),new ByteArrayInputStream("good".getBytes()));
  }};
  assertThrows(IOException.class,()->remote.download("https://9.9.9.9/a",game.resolve("first"),4,firstCancel));
  var waitingCancel=new AtomicBoolean();
  try(var pool=Executors.newFixedThreadPool(2)){
   var waiting=pool.submit(()->{remote.awaitSource("https://9.9.9.9/b",waitingCancel);remote.download("https://9.9.9.9/b",game.resolve("waiting"),4,waitingCancel);return null;});
   var other=pool.submit(()->{remote.download(URL,game.resolve("other"),4,new AtomicBoolean());return null;});
   try{other.get(2,TimeUnit.SECONDS);assertEquals(2,requests.get());assertFalse(waiting.isDone());}
   finally{waitingCancel.set(true);}
   assertThrows(ExecutionException.class,()->waiting.get(2,TimeUnit.SECONDS));
  }
 }
 @Test void cleanupPreservesReferencedObjectsAndRecentPartials()throws Exception{
  var cache=new Cache(game,new Remote());Path object=cache.path(file().sha256());Files.createDirectories(object.getParent());Files.writeString(object,"good");
  Path partial=object.resolveSibling(object.getFileName()+"."+"a".repeat(64)+".part");Files.writeString(partial,"go");
  assertEquals(0,cache.clearUnused(Set.of(file().sha256())));assertTrue(Files.exists(partial));
  Files.setLastModifiedTime(partial,java.nio.file.attribute.FileTime.from(java.time.Instant.now().minus(java.time.Duration.ofDays(8))));
  assertEquals(2,cache.clearUnused(Set.of(file().sha256())));assertFalse(Files.exists(partial));assertTrue(Files.exists(object));
 }
 @Test void throttledSourceImmediatelyUsesOtherMirror()throws Exception{
  var calls=new ArrayList<String>();var remote=new Remote(){@Override HttpResponse<InputStream> send(HttpRequest r){calls.add(r.uri().getHost());return r.uri().getHost().equals("8.8.4.4")?response(r,429,Map.of("Retry-After",List.of("60")),InputStream.nullInputStream()):response(r,200,Map.of(),new ByteArrayInputStream("good".getBytes()));}};
  var entry=new Manifest.FileEntry("mod","mods/test.jar","1",List.of("https://8.8.4.4/mod.jar",URL),file().sha256(),4,"enforce");
  assertEquals("good",Files.readString(new Cache(game,remote).obtain(entry,new AtomicBoolean())));assertEquals(List.of("8.8.4.4","1.1.1.1"),calls);
 }
 public static final class LockHolder {
  public static void main(String[] args)throws Exception{Path lock=Path.of(args[0]);try(var channel=java.nio.channels.FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lease=channel.lock()){System.out.println("locked");System.out.flush();System.in.read();}}
 }
 @Test void otherJavaProcessProtectsPartialFromCleanupAndDownload()throws Exception{
  var calls=new AtomicInteger();var remote=new Remote(){@Override public void download(String u,Path p,long n,AtomicBoolean c)throws IOException{calls.incrementAndGet();Files.writeString(p,"good");}};
  var cache=new Cache(game,remote);Path object=cache.path(file().sha256());Files.createDirectories(object.getParent());
  Path partial=object.resolveSibling(object.getFileName()+"."+"b".repeat(64)+".part");Files.writeString(partial,"go");Files.setLastModifiedTime(partial,java.nio.file.attribute.FileTime.fromMillis(1));
  Path locks=game.resolve("anthub/cache/objects/locks");Files.createDirectories(locks);Path lock=locks.resolve(Math.floorMod(object.getFileName().toString().hashCode(),256)+".lock");
  var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("java.class.path"),LockHolder.class.getName(),lock.toString()).redirectErrorStream(true).start();
  try{assertEquals("locked",process.inputReader().readLine());assertEquals(0,cache.clearUnused(Set.of()));var cancel=new AtomicBoolean();
   try(var pool=Executors.newSingleThreadExecutor()){var download=pool.submit(()->cache.obtain(file(),cancel));try{assertThrows(TimeoutException.class,()->download.get(200,TimeUnit.MILLISECONDS));assertEquals(0,calls.get());}finally{cancel.set(true);}assertThrows(ExecutionException.class,()->download.get(2,TimeUnit.SECONDS));}
  }finally{process.getOutputStream().write(1);process.getOutputStream().flush();if(!process.waitFor(3,TimeUnit.SECONDS))process.destroyForcibly();}
 }

}
