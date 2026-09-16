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
    public record Update(String version,Manifest.FileEntry artifact){}
    private final Remote remote;
    public CoreUpdater(Remote remote){this.remote=remote;}
    public Optional<Update> check(String runningVersion,String minecraft,String neoForge)throws Exception{
        var releases=JsonParser.parseString(new String(remote.bytes(RELEASES,4*1024*1024),StandardCharsets.UTF_8));
        if(!releases.isJsonArray())throw new IllegalArgumentException("Invalid official release list");
        var versions=new ArrayList<String>();
        for(var value:releases.getAsJsonArray()){
            var release=value.getAsJsonObject();if(release.get("draft").getAsBoolean())continue;
            String tag=Json.str(release,"tag_name");
            if(!tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?"))continue;
            String version=tag.substring(1);
            boolean preview=version.contains("-")||(release.has("prerelease")&&release.get("prerelease").getAsBoolean());
            if(!runningVersion.contains("-")&&preview)continue;
            if(Versions.compare(version,runningVersion)>0)versions.add(version);
        }
        versions.sort((a,b)->Versions.compare(b,a));
        for(String version:versions){
            String base=REPOSITORY+"/releases/download/v"+version+"/";
            byte[] bytes=remote.bytes(base+"core.json",1024*1024);
            var descriptor=Json.parse(new String(bytes,StandardCharsets.UTF_8));Schema.validate("core-release",descriptor);
            if(!version.equals(Json.str(descriptor,"version")))throw new IllegalArgumentException("Official release version mismatch");
            for(var value:descriptor.getAsJsonArray("artifacts")){
                var artifact=value.getAsJsonObject();
                if(!minecraft.equals(Json.str(artifact,"minecraft"))||!compatibleLoader(neoForge,Json.str(artifact,"neoForge")))continue;
                if(artifact.get("java").getAsInt()>Runtime.version().feature())continue;
                String url=Json.str(artifact,"url");
                if(!url.equals(base+"anthub-"+version+"-mc"+minecraft+"-neoforge.jar"))throw new IllegalArgumentException("Update must belong to its official release");
                return Optional.of(new Update(version,new Manifest.FileEntry("anthub","mods/anthub.jar",version,java.util.List.of(url),Json.str(artifact,"sha256"),artifact.get("size").getAsLong(),"enforce")));
            }
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
        cache.obtain(update.artifact(),new AtomicBoolean());
        var plan=new Planner.Plan(UUID.randomUUID().toString(),"anthub-core",List.of(new Planner.Change(relative,Hashes.sha256(jar),update.artifact().sha256())),Map.of(),Set.of(),List.of(),update.artifact().size());
        JsonObject next=currentState.deepCopy();next.addProperty("coreVersion",update.version());new Transactions(game).prepare(plan,new byte[0],next);return plan.id();
    }
}
