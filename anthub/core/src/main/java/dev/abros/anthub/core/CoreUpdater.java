package dev.abros.anthub.core;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Updates come only from published releases of the official GitHub repository. */
public final class CoreUpdater {
    public static final String REPOSITORY="https://github.com/abrosdaniel/anthub";
    public static final String RELEASES="https://api.github.com/repos/abrosdaniel/anthub/releases?per_page=100";
    public record Update(String version,Manifest.FileEntry artifact,boolean preservesProtocols){}
    private static final java.util.concurrent.ExecutorService METADATA=new java.util.concurrent.ThreadPoolExecutor(4,4,0,java.util.concurrent.TimeUnit.SECONDS,
        new java.util.concurrent.ArrayBlockingQueue<>(100),r->{var t=new Thread(r,"AntHub release metadata");t.setDaemon(true);return t;},new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private final Remote remote;
    public CoreUpdater(Remote remote){this.remote=remote;}
    public Optional<Update> check(String runningVersion,String minecraft,String neoForge)throws Exception{
        return available(runningVersion,minecraft,neoForge,1).stream().findFirst();
    }
    public List<Update> available(String runningVersion,String minecraft,String neoForge,int limit)throws Exception{
        return releases(runningVersion,minecraft,neoForge,limit,false);
    }
    public List<Update> releases(String runningVersion,String minecraft,String neoForge,int limit,boolean includeOlder)throws Exception{
        if(limit<1||limit>100)throw new IllegalArgumentException("Release list limit");
        var versions=new java.util.TreeSet<String>((a,b)->Versions.compare(b,a));
        for(int page=1;page<=100;page++){
            String url=RELEASES+(page==1?"":"&page="+page);
            var releases=JsonParser.parseString(new String(remote.bytes(url,4*1024*1024),StandardCharsets.UTF_8));
            if(!releases.isJsonArray())throw new IllegalArgumentException("Invalid official release list");
            for(var value:releases.getAsJsonArray()){
                var release=value.getAsJsonObject();if(release.get("draft").getAsBoolean()||(release.has("prerelease")&&release.get("prerelease").getAsBoolean()))continue;
                String tag=Json.str(release,"tag_name");if(!tag.matches("v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))continue;
                String version=tag.substring(1);if(!version.equals(runningVersion)&&(includeOlder||Versions.compare(version,runningVersion)>0))versions.add(version);
            }
            if(releases.getAsJsonArray().size()<100)break;
            if(page==100)throw new java.io.IOException("Too many official releases");
        }
        var jobs=new ArrayList<java.util.concurrent.Future<Optional<Update>>>();
        var completed=new java.util.concurrent.ExecutorCompletionService<Optional<Update>>(METADATA);
        var found=new ArrayList<Update>();Exception failure=null;
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        var candidates=versions.stream().limit(100).iterator();
        try{
            while(candidates.hasNext()&&found.size()<limit&&System.nanoTime()<deadline){
                int pending=0;
                while(pending<4&&candidates.hasNext()){String version=candidates.next();jobs.add(completed.submit(()->descriptor(version,minecraft,neoForge)));pending++;}
                while(pending>0){
                    var job=completed.poll(Math.max(1,deadline-System.nanoTime()),java.util.concurrent.TimeUnit.NANOSECONDS);
                    if(job==null){failure=new java.util.concurrent.TimeoutException();break;}
                    pending--;try{job.get().ifPresent(found::add);}catch(java.util.concurrent.ExecutionException bad){failure=bad;}
                }
            }
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}
        finally{for(var job:jobs)if(!job.isDone())job.cancel(true);}
        if(found.isEmpty()&&failure!=null)throw new java.io.IOException("Не удалось загрузить доступные версии AntHub. Повторите позже.",failure);
        return found.stream().sorted((a,b)->Versions.compare(b.version(),a.version())).limit(limit).toList();
    }
    private Optional<Update> descriptor(String version,String minecraft,String neoForge)throws Exception{
        String base=REPOSITORY+"/releases/download/v"+version+"/";
        var descriptor=Json.parse(new String(remote.bytes(base+"core.json",1024*1024),StandardCharsets.UTF_8));Schema.validate("core-release",descriptor);
        if(!version.equals(Json.str(descriptor,"version")))throw new IllegalArgumentException("Official release version mismatch");
        for(var value:descriptor.getAsJsonArray("artifacts")){
            var artifact=value.getAsJsonObject();
            if(!minecraft.equals(Json.str(artifact,"minecraft"))||!compatibleLoader(neoForge,Json.str(artifact,"neoForge")))continue;
            if(artifact.get("java").getAsInt()>Runtime.version().feature())continue;
            String url=Json.str(artifact,"url");
            if(!url.equals(base+"anthub-"+version+"-mc"+minecraft+"-neoforge.jar"))throw new IllegalArgumentException("Update must belong to its official release");
            return Optional.of(new Update(version,new Manifest.FileEntry("anthub","mods/anthub-"+version+"-mc"+minecraft+"-neoforge.jar",version,List.of(url),Json.str(artifact,"sha256"),artifact.get("size").getAsLong(),"enforce"),WireProtocols.compatible(descriptor.getAsJsonObject("protocols"))));
        }
        return Optional.empty();
    }
    private static boolean compatibleLoader(String installed,String minimum){
        String[] actual=installed.split("\\."),required=minimum.split("\\.");
        return actual.length==3&&required.length==3&&actual[0].equals(required[0])&&actual[1].equals(required[1])&&Versions.compare(installed,minimum)>=0;
    }
    public String stage(Path game,Path loadedJar,Update update,Cache cache,JsonObject currentState)throws Exception{
        Path root=game.toRealPath(),jar=loadedJar.toRealPath();if(!jar.startsWith(root.resolve("mods"))||!Files.isRegularFile(jar))throw new IllegalArgumentException("Core update requires an installed JAR in this game directory");
        String relative=root.relativize(jar).toString().replace('\\','/');SafePaths.validate(relative);
        String destination=update.artifact().path();
        if(!relative.equals(destination)&&Files.exists(SafePaths.resolve(root,destination)))throw new IllegalArgumentException("Файл выбранной версии уже находится в mods. Уберите дубликат AntHub перед обновлением.");
        cache.obtain(update.artifact(),new AtomicBoolean());
        var changes=new ArrayList<Planner.Change>();
        if(relative.equals(destination))changes.add(new Planner.Change(relative,Hashes.sha256(jar),update.artifact().sha256()));
        else {changes.add(new Planner.Change(relative,Hashes.sha256(jar),null));changes.add(new Planner.Change(destination,null,update.artifact().sha256()));}
        var plan=new Planner.Plan(UUID.randomUUID().toString(),"anthub-core",List.copyOf(changes),Map.of(),Set.of(),List.of(),update.artifact().size());
        JsonObject next=currentState.deepCopy();next.addProperty("coreVersion",update.version());new Transactions(game).prepare(plan,new byte[0],next);return plan.id();
    }
}
