package dev.abros.anthub.bootstrap;
import java.nio.file.*;
import java.nio.channels.*;
import java.io.*;
/** Acquired before normal mods are discovered; retained until the JVM terminates. */
public final class LaunchGate {
    private FileChannel channel;private FileLock lock;
    public void enter(Path game)throws IOException{
        Path data=game.toRealPath().resolve("anthub");if(Files.isSymbolicLink(data))throw new IOException("Unsafe AntHub directory");Files.createDirectories(data);
        Path pending=data.resolve("pending.json"),lockPath=data.resolve("apply.lock");
        if(Files.exists(pending))throw new IOException("AntHub is applying or recovering an update. Wait for the helper to finish, then launch Minecraft again. See anthub/runtime/helper.log.");
        if(Files.isSymbolicLink(lockPath))throw new IOException("Unsafe update lock");
        channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);
        lock=channel.tryLock(0,Long.MAX_VALUE,true);
        if(lock==null||Files.exists(pending)){close();throw new IOException("AntHub update in progress. Launch Minecraft again after completion.");}
        Path epoch=data.resolve("mutation.epoch");
        if(Files.exists(epoch)&&Files.getLastModifiedTime(epoch).toInstant().isAfter(ProcessHandle.current().info().startInstant().orElseThrow())){close();throw new IOException("AntHub completed an update while this launch was starting. Start Minecraft again to load a consistent pack.");}
    }
    public void close()throws IOException{if(lock!=null)lock.close();if(channel!=null)channel.close();}
}
