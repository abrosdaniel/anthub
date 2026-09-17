package dev.abros.anthub.core;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
/** Project identity is the selected HTTPS GitHub repository; file integrity uses SHA-256. */
public final class RepositoryClient {
    public record Release(Manifest manifest,byte[] bytes,boolean trusted,boolean offline,String checkedAt,String hash){
        public Release(Manifest manifest,byte[] bytes,boolean trusted,boolean offline,String checkedAt){this(manifest,bytes,trusted,offline,checkedAt,Hashes.sha256(bytes));}
    }
    private final Path data;private final Remote remote;
    public RepositoryClient(Path game,Remote remote)throws IOException{data=game.resolve("anthub");Files.createDirectories(data.resolve("projects"));this.remote=remote;}
    private JsonObject get(String url)throws Exception{return Json.parse(new String(remote.bytes(url,8*1024*1024),StandardCharsets.UTF_8));}
    private Path project(String repo){return data.resolve("projects").resolve(Hashes.sha256(repo.getBytes(StandardCharsets.UTF_8)));}
    public Release fetchOrCached(String repository)throws Exception{
        try{return fetch(repository);}catch(Remote.Unavailable|java.net.UnknownHostException network){
            if(network instanceof Remote.HttpFailure http&&(http.status==404||http.status==410))throw http;
            try{return cached(repository);}catch(NoSuchFileException missing){network.addSuppressed(missing);throw network;}
        }
    }
    public Release fetch(String repository)throws Exception{
        String repo=Repositories.normalize(repository);Path p=project(repo);Files.createDirectories(p);
        String version=latestVersion(repo);
        String base=repo+"/releases/download/pack-v"+version+"/";
        String expected=new String(remote.bytes(base+"anthub.lock.sha256",128),StandardCharsets.US_ASCII).strip();Hashes.check(expected);
        byte[] bytes=remote.bytes(base+"anthub.lock.json",8*1024*1024);String hash=Hashes.sha256(bytes);
        if(!hash.equals(expected))throw new IOException("Lock hash mismatch");
        Manifest m=Manifest.parse(Json.parse(new String(bytes,StandardCharsets.UTF_8)));
        if(!m.repository().equals(repo)||!m.version().equals(version))throw new IOException("Release identity mismatch");
        Path latestPath=p.resolve("latest.json");
        if(Files.exists(latestPath)){var old=Json.read(latestPath);int order=Versions.compare(version,Json.str(old,"version"));
            if(order<0||(order==0&&!hash.equals(Json.str(old,"hash"))))throw new IOException("Release rollback or mutation rejected");}
        String checked=java.time.Instant.now().toString();Path snapshot=p.resolve(hash);Files.createDirectories(snapshot);Files.write(snapshot.resolve("lock.json"),bytes);
        JsonObject latest=new JsonObject();latest.addProperty("version",version);latest.addProperty("hash",hash);latest.addProperty("checkedAt",checked);Json.write(latestPath,latest);
        return new Release(m,bytes,Files.exists(p.resolve("repository-trust.json")),false,checked);
    }
    private String latestVersion(String repo)throws Exception{
        String latest=null;
        for(int page=1;page<=100;page++){
            String url="https://api.github.com/repos/"+repo.substring("https://github.com/".length())+"/releases?per_page=100&page="+page;
            var json=JsonParser.parseString(new String(remote.bytes(url,8*1024*1024),StandardCharsets.UTF_8));
            if(!json.isJsonArray())throw new IOException("Invalid GitHub release list");var releases=json.getAsJsonArray();
            for(var value:releases){var release=value.getAsJsonObject();
                if(release.get("draft").getAsBoolean()||release.get("prerelease").getAsBoolean())continue;
                String tag=Json.str(release,"tag_name");if(!tag.matches("pack-v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))continue;
                String version=tag.substring(6);if(latest==null||Versions.compare(version,latest)>0)latest=version;
            }
            if(releases.size()<100){if(latest==null)throw new IOException("В проекте нет опубликованной сборки pack-vA.B.C");return latest;}
        }
        throw new IOException("Too many GitHub releases; cannot safely select the latest pack");
    }
    public Release snapshot(String repository,String hash)throws Exception{
        String repo=Repositories.normalize(repository);Hashes.check(hash);Path p=project(repo);
        if(!Files.exists(p.resolve("repository-trust.json")))throw new NoSuchFileException("No trusted project snapshot");
        byte[] bytes=Files.readAllBytes(p.resolve(hash).resolve("lock.json"));if(!Hashes.sha256(bytes).equals(hash))throw new IOException("Cached lock damaged");
        Manifest m=Manifest.parse(Json.parse(new String(bytes,StandardCharsets.UTF_8)));if(!m.repository().equals(repo))throw new IOException("Cached repository mismatch");
        return new Release(m,bytes,true,true,"");
    }
    public Release cached(String repository)throws Exception{
        String repo=Repositories.normalize(repository);JsonObject latest=Json.read(project(repo).resolve("latest.json"));Release release=snapshot(repo,Json.str(latest,"hash"));return new Release(release.manifest(),release.bytes(),true,true,Json.str(latest,"checkedAt"));
    }
    public void trust(Release release)throws IOException{Path p=project(release.manifest().repository());Files.createDirectories(p);Json.write(p.resolve("repository-trust.json"),Map.of("repository",release.manifest().repository()));}
    public List<String> registry(String url)throws Exception{JsonObject j=get(url);Schema.validate("registry",j);List<String> result=new ArrayList<>();for(var e:j.getAsJsonArray("projects"))result.add(Repositories.normalize(Json.str(e.getAsJsonObject(),"repository")));return List.copyOf(result);}
}
