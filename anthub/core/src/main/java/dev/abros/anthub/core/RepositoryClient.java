package dev.abros.anthub.core;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
/** Project identity is the selected HTTPS GitHub repository; file integrity uses SHA-256. */
public final class RepositoryClient {
    public record Release(Manifest manifest,byte[] bytes,boolean trusted,boolean offline,String checkedAt){}
    private final Path data;private final Remote remote;
    public RepositoryClient(Path game,Remote remote)throws IOException{data=game.resolve("anthub");Files.createDirectories(data.resolve("projects"));this.remote=remote;}
    private JsonObject get(String url)throws Exception{return Json.parse(new String(remote.bytes(url,8*1024*1024),StandardCharsets.UTF_8));}
    private Path project(String repo){return data.resolve("projects").resolve(Hashes.sha256(repo.getBytes(StandardCharsets.UTF_8)));}
    public Release fetchOrCached(String repository)throws Exception{
        try{return fetch(repository);}catch(Remote.Unavailable|java.net.UnknownHostException network){
            try{return cached(repository);}catch(NoSuchFileException missing){network.addSuppressed(missing);throw network;}
        }
    }
    public Release fetch(String repository)throws Exception{
        String repo=Repositories.normalize(repository);Path p=project(repo);Files.createDirectories(p);
        JsonObject pointer=get(Repositories.raw(repo,"channels/stable.json"));Schema.validate("channel",pointer);
        if(!repo.equals(Json.str(pointer,"repository"))||!"stable".equals(Json.str(pointer,"channel")))throw new IOException("Channel identity mismatch");
        String url=Json.str(pointer,"lockUrl");
        if(!url.startsWith(repo+"/releases/download/"))throw new IOException("Manifest must belong to the selected GitHub repository");
        long sequence=pointer.get("sequence").getAsLong();Path channelState=p.resolve("stable-pointer.json");
        if(Files.exists(channelState)){JsonObject old=Json.read(channelState);long previous=old.get("sequence").getAsLong();if(sequence<previous||(sequence==previous&&!Json.str(old,"lockSha256").equals(Json.str(pointer,"lockSha256"))))throw new IOException("Release rollback or mutation rejected");}
        byte[] bytes=remote.bytes(url,8*1024*1024);String hash=Hashes.sha256(bytes);if(!hash.equals(Json.str(pointer,"lockSha256")))throw new IOException("Lock hash mismatch");
        Manifest m=Manifest.parse(Json.parse(new String(bytes,StandardCharsets.UTF_8)));JsonObject rel=m.json().getAsJsonObject("release");
        if(!m.repository().equals(repo)||!m.version().equals(Json.str(pointer,"version"))||!"stable".equals(Json.str(rel,"channel"))||sequence!=rel.get("sequence").getAsLong())throw new IOException("Release identity mismatch");
        String checked=java.time.Instant.now().toString();Path snapshot=p.resolve(hash);Files.createDirectories(snapshot);Files.write(snapshot.resolve("lock.json"),bytes);Json.write(channelState,pointer);
        JsonObject latest=new JsonObject();latest.addProperty("hash",hash);latest.addProperty("checkedAt",checked);Json.write(p.resolve("stable-latest.json"),latest);
        return new Release(m,bytes,Files.exists(p.resolve("repository-trust.json")),false,checked);
    }
    public Release snapshot(String repository,String hash)throws Exception{
        String repo=Repositories.normalize(repository);Hashes.check(hash);Path p=project(repo);
        if(!Files.exists(p.resolve("repository-trust.json")))throw new NoSuchFileException("No trusted project snapshot");
        byte[] bytes=Files.readAllBytes(p.resolve(hash).resolve("lock.json"));if(!Hashes.sha256(bytes).equals(hash))throw new IOException("Cached lock damaged");
        Manifest m=Manifest.parse(Json.parse(new String(bytes,StandardCharsets.UTF_8)));if(!m.repository().equals(repo))throw new IOException("Cached repository mismatch");
        return new Release(m,bytes,true,true,"");
    }
    public Release cached(String repository)throws Exception{
        String repo=Repositories.normalize(repository);JsonObject latest=Json.read(project(repo).resolve("stable-latest.json"));Release release=snapshot(repo,Json.str(latest,"hash"));return new Release(release.manifest(),release.bytes(),true,true,Json.str(latest,"checkedAt"));
    }
    public void trust(Release release)throws IOException{Path p=project(release.manifest().repository());Files.createDirectories(p);Json.write(p.resolve("repository-trust.json"),Map.of("repository",release.manifest().repository()));}
    public List<String> registry(String url)throws Exception{JsonObject j=get(url);Schema.validate("registry",j);List<String> result=new ArrayList<>();for(var e:j.getAsJsonArray("projects"))result.add(Repositories.normalize(Json.str(e.getAsJsonObject(),"repository")));return List.copyOf(result);}
}
