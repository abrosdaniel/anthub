package dev.abros.anthub.bootstrap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.channels.*;
class LaunchGateTest {
 @TempDir Path game;
 @Test void pendingStopsLaunchBeforeDiscovery()throws Exception{Files.createDirectories(game.resolve("anthub"));Files.writeString(game.resolve("anthub/pending.json"),"{}");assertThrows(java.io.IOException.class,()->new LaunchGate().enter(game));}
 @Test void runningGamePreventsUpdaterExclusiveLock()throws Exception{var gate=new LaunchGate();gate.enter(game);try(var c=FileChannel.open(game.resolve("anthub/apply.lock"),StandardOpenOption.WRITE)){assertThrows(OverlappingFileLockException.class,()->c.tryLock());}finally{gate.close();}}
 @Test void updaterStopsNewGame()throws Exception{Files.createDirectories(game.resolve("anthub"));try(var c=FileChannel.open(game.resolve("anthub/apply.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var l=c.lock()){assertThrows(Exception.class,()->new LaunchGate().enter(game));}}
}
