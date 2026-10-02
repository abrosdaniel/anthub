package dev.abros.anthub.helper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
class ParentWaitTest {
 private static final Instant START=Instant.parse("2026-10-02T10:00:00Z");
 @Test void exitedParentWithoutMetadataDoesNotBlockUpdate()throws Exception{Main.awaitParent(new Parent(false,null),START);}
 @Test void liveParentWithoutMetadataCannotBeAccepted(){assertThrows(IllegalStateException.class,()->Main.awaitParent(new Parent(true,null),START));}
 @Test void reusedLivePidCannotBeAccepted(){assertThrows(IllegalStateException.class,()->Main.awaitParent(new Parent(true,START.plusSeconds(1)),START));}
 @Test void verifiedParentMustExitBeforeUpdate()throws Exception{var parent=new Parent(true,START);var waiting=CompletableFuture.runAsync(()->{try{Main.awaitParent(parent,START);}catch(Exception e){throw new CompletionException(e);}});assertTrue(parent.registered.await(5,TimeUnit.SECONDS));assertFalse(waiting.isDone());parent.alive=false;parent.exit.complete(parent);waiting.get(5,TimeUnit.SECONDS);}
 private static final class Parent implements ProcessHandle {
  volatile boolean alive;final Instant start;final CompletableFuture<ProcessHandle> exit=new CompletableFuture<>();final CountDownLatch registered=new CountDownLatch(1);
  Parent(boolean alive,Instant start){this.alive=alive;this.start=start;if(!alive)exit.complete(this);}
  public long pid(){return 123;}
  public Optional<ProcessHandle> parent(){return Optional.empty();}
  public Stream<ProcessHandle> children(){return Stream.empty();}
  public Stream<ProcessHandle> descendants(){return Stream.empty();}
  public Info info(){return new Info(){public Optional<String> command(){return Optional.empty();}public Optional<String> commandLine(){return Optional.empty();}public Optional<String[]> arguments(){return Optional.empty();}public Optional<Instant> startInstant(){return Optional.ofNullable(start);}public Optional<Duration> totalCpuDuration(){return Optional.empty();}public Optional<String> user(){return Optional.empty();}};}
  public CompletableFuture<ProcessHandle> onExit(){registered.countDown();return exit;}
  public boolean supportsNormalTermination(){return true;}
  public boolean destroy(){throw new UnsupportedOperationException();}
  public boolean destroyForcibly(){throw new UnsupportedOperationException();}
  public boolean isAlive(){return alive;}
  public int compareTo(ProcessHandle other){return Long.compare(pid(),other.pid());}
 }
}
