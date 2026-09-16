package dev.abros.anthub.core;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
public final class Transactions {
    public static final class BusyException extends IOException { public BusyException(){super("Minecraft or another update is still running");} }
    private final Path game, data;
    public Transactions(Path game)throws IOException{
        this.game=game.toRealPath();this.data=this.game.resolve("anthub");
        if(Files.isSymbolicLink(data))throw new IOException("AntHub directory cannot be a symlink");Files.createDirectories(data);checkInternal(data);
    }
    private void checkInternal(Path p)throws IOException{
        for(Path q=p;q!=null&&!q.equals(game);q=q.getParent())if(Files.isSymbolicLink(q)||(Files.exists(q)&&!q.toRealPath().startsWith(game)))throw new IOException("Unsafe internal directory");
    }
    public Path directory(String id)throws IOException{UUID.fromString(id);Path p=data.resolve("transactions").resolve(id);checkInternal(p);return p;}
    public Path prepare(Planner.Plan plan,byte[] manifest,JsonObject nextState)throws IOException{
        Path guard=data.resolve("prepare.lock");checkInternal(guard);
        try(FileChannel channel=FileChannel.open(guard,StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=channel.tryLock()){
            if(lock==null)throw new BusyException();
            return prepareLocked(plan,manifest,nextState);
        }catch(OverlappingFileLockException busy){throw new BusyException();}
    }
    private Path prepareLocked(Planner.Plan plan,byte[] lock,JsonObject nextState)throws IOException{
        if(!plan.conflicts().isEmpty())throw new IOException(String.join("\n",plan.conflicts()));
        if(Files.exists(data.resolve("pending.json")))throw new IOException("An existing update must finish or recover first");Path p=directory(plan.id());Files.createDirectories(p);
        Json.write(p.resolve("plan.json"),plan);Files.write(p.resolve("lock.json"),lock);
        Json.write(p.resolve("next-state.json"),nextState);
        Path state=data.resolve("state.json");Json.write(p.resolve("previous-state.json"),Files.exists(state)?Json.read(state):new JsonObject());
        Json.write(p.resolve("journal.json"),journal("READY",0));Json.write(data.resolve("pending.json"),java.util.Map.of("id",plan.id()));return p;
    }
    private void clearPending(String id)throws IOException{
        Path pending=data.resolve("pending.json");
        if(Files.exists(pending)&&id.equals(Json.str(Json.read(pending),"id")))Files.delete(pending);
    }
    private void requirePendingOwner(String id)throws IOException{
        Path pending=data.resolve("pending.json");
        if(Files.exists(pending)&&!id.equals(Json.str(Json.read(pending),"id")))throw new IOException("Another transaction is pending");
    }
    private JsonObject journal(String status,int done){JsonObject j=new JsonObject();j.addProperty("status",status);j.addProperty("at",java.time.Instant.now().toString());j.addProperty("done",done);return j;}
    public void apply(String id)throws IOException{
        Path dir=directory(id);checkInternal(data.resolve("apply.lock"));
        try(FileChannel channel=FileChannel.open(data.resolve("apply.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=channel.tryLock()){
            if(lock==null)throw new BusyException();
            var plan=Json.GSON.fromJson(Json.read(dir.resolve("plan.json")),Planner.Plan.class);
            if(!id.equals(plan.id())||!plan.conflicts().isEmpty())throw new IOException("Invalid transaction");
            var j=Json.read(dir.resolve("journal.json"));String status=Json.str(j,"status");
            if(status.equals("COMMITTED")){clearPending(id);return;}
            requirePendingOwner(id);
            if(!status.equals("READY")){recoverLocked(dir,plan);return;}
            JsonObject current=Files.exists(data.resolve("state.json"))?Json.read(data.resolve("state.json")):new JsonObject();
            if(!current.equals(Json.read(dir.resolve("previous-state.json"))))throw new IOException("PRECONDITION_CHANGED: state");
            Set<String> seen=new HashSet<>();
            for(var c:plan.changes()){
                if(!seen.add(SafePaths.key(c.path())))throw new IOException("Duplicate destination");
                Path dest=SafePaths.resolve(game,c.path());
                if(!Objects.equals(Planner.hash(dest),c.before()))throw new IOException("PRECONDITION_CHANGED: "+c.path());
                if(c.after()!=null){Hashes.check(c.after());Path object=object(c.after());if(!Hashes.sha256(object).equals(c.after()))throw new IOException("Corrupted cache");}
            }
            Path backup=dir.resolve("preimages");Files.createDirectories(backup);
            for(int i=0;i<plan.changes().size();i++){
                var c=plan.changes().get(i);if(c.before()!=null){Path dest=SafePaths.resolve(game,c.path());Path b=backup.resolve(Integer.toString(i));Files.copy(dest,b,StandardCopyOption.REPLACE_EXISTING);if(!Hashes.sha256(b).equals(c.before()))throw new IOException("Backup mismatch");}
            }
            Json.write(dir.resolve("journal.json"),journal("APPLYING",0));
            try{
                for(int i=0;i<plan.changes().size();i++){
                    var c=plan.changes().get(i);Path dest=SafePaths.resolve(game,c.path());
                    if(!Objects.equals(Planner.hash(dest),c.before()))throw new IOException("PRECONDITION_CHANGED: "+c.path());
                    if(c.after()==null)Files.deleteIfExists(dest);else materialize(object(c.after()),dest,c.after());
                    Json.write(dir.resolve("journal.json"),journal("APPLYING",i+1));
                    if(Integer.getInteger("anthub.test.failAfter",-1)==i+1)throw new IOException("Injected failure");
                }
                Json.write(data.resolve("state.json"),Json.read(dir.resolve("next-state.json")));
                Json.write(dir.resolve("journal.json"),journal("COMMITTED",plan.changes().size()));Files.writeString(data.resolve("mutation.epoch"),id);clearPending(plan.id());
            }catch(IOException e){try{recoverLocked(dir,plan);}catch(IOException recovery){e.addSuppressed(recovery);}throw e;}
        }
    }
    private Path object(String hash)throws IOException{Hashes.check(hash);Path p=data.resolve("cache/objects").resolve(hash.substring(0,2)).resolve(hash);checkInternal(p);if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Cache object missing");return p;}
    private void materialize(Path from,Path dest,String expected)throws IOException{
        Files.createDirectories(dest.getParent());Path tmp=Files.createTempFile(dest.getParent(),".anthub-",".stage");
        try{Files.copy(from,tmp,StandardCopyOption.REPLACE_EXISTING);if(!Hashes.sha256(tmp).equals(expected))throw new IOException("Stage mismatch");try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.WRITE)){c.force(true);}SafePaths.resolve(game,game.relativize(dest).toString().replace('\\','/'));Json.move(tmp,dest);}finally{Files.deleteIfExists(tmp);}
    }
    public void abortReady(String id)throws IOException{
        Path dir=directory(id);try(FileChannel channel=FileChannel.open(data.resolve("apply.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=channel.tryLock()){
            if(lock==null)throw new BusyException();String status=Json.str(Json.read(dir.resolve("journal.json")),"status");
            requirePendingOwner(id);
            if(!status.equals("READY"))return;Json.write(dir.resolve("journal.json"),journal("CANCELLED",0));
            Path pending=data.resolve("pending.json");if(Files.exists(pending)&&id.equals(Json.str(Json.read(pending),"id")))Files.delete(pending);
        }
    }
    public void recover(String id)throws IOException{
        Path dir=directory(id);
        try(FileChannel channel=FileChannel.open(data.resolve("apply.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock lock=channel.tryLock()){
            if(lock==null)throw new IOException("Transaction is busy");var plan=Json.GSON.fromJson(Json.read(dir.resolve("plan.json")),Planner.Plan.class);recoverLocked(dir,plan);
        }
    }
    private void recoverLocked(Path dir,Planner.Plan plan)throws IOException{
        String savedStatus=Json.str(Json.read(dir.resolve("journal.json")),"status");
        if(savedStatus.equals("COMMITTED")||savedStatus.equals("CANCELLED")||savedStatus.equals("ROLLED_BACK")){clearPending(plan.id());return;}
        requirePendingOwner(plan.id());
        if(savedStatus.equals("READY")){Json.write(dir.resolve("journal.json"),journal("CANCELLED",0));clearPending(plan.id());return;}

        try{
            for(int i=plan.changes().size()-1;i>=0;i--){var c=plan.changes().get(i);Path dest=SafePaths.resolve(game,c.path());String current=Planner.hash(dest);
                if(Objects.equals(current,c.before()))continue;
                if(!Objects.equals(current,c.after()))throw new IOException("Recovery collision: "+c.path());
                if(c.before()==null)Files.deleteIfExists(dest);else{Path b=dir.resolve("preimages").resolve(Integer.toString(i));checkInternal(b);if(!Hashes.sha256(b).equals(c.before()))throw new IOException("Recovery backup mismatch");materialize(b,dest,c.before());}
            }
            Json.write(data.resolve("state.json"),Json.read(dir.resolve("previous-state.json")));
            Json.write(dir.resolve("journal.json"),journal("ROLLED_BACK",0));Files.writeString(data.resolve("mutation.epoch"),plan.id());clearPending(plan.id());
        }catch(IOException e){Json.write(dir.resolve("journal.json"),journal("RECOVERY_REQUIRED",0));throw e;}
    }
}
