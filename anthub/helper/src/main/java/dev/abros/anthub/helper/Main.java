package dev.abros.anthub.helper;
import dev.abros.anthub.core.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
public final class Main {
    public static void main(String[] args)throws Exception{
        if(args.length==1&&args[0].equals("--version")){System.out.println("AntHub helper protocol 1");return;}
        if(args.length<3)throw new IllegalArgumentException("Usage: helper.jar apply|recover GAME_DIRECTORY TRANSACTION_ID [PID START_INSTANT]");
        String operation=args[0];Path game=Path.of(args[1]);String id=args[2];
        if(operation.equals("apply")){
            if(args.length!=5)throw new IllegalArgumentException("Parent PID and start instant required");
            long pid=Long.parseLong(args[3]);Instant expected=Instant.parse(args[4]);
            var parent=ProcessHandle.of(pid);
            if(parent.isPresent()){
                if(!parent.get().info().startInstant().orElseThrow().equals(expected))throw new IllegalStateException("Parent process identity changed");
                parent.get().onExit().get(24,TimeUnit.HOURS);
            }
            long deadline=System.nanoTime()+TimeUnit.HOURS.toNanos(24);
            while(true){try{new Transactions(game).apply(id);break;}catch(Transactions.BusyException busy){if(System.nanoTime()>deadline)throw busy;Thread.sleep(1000);}catch(java.io.IOException failure){try{new Transactions(game).abortReady(id);}catch(java.io.IOException abort){failure.addSuppressed(abort);}throw failure;}}
        }else if(operation.equals("recover")){new Transactions(game).recover(id);}
        else throw new IllegalArgumentException("Unknown operation");
    }
}
