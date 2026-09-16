package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.*;
public record Manifest(JsonObject json,String repository,String id,String name,String version,String minecraft,String neoForge,String minCore,List<Component> components,List<FileEntry> files,List<Server> servers) {
    public record Component(String id,String name,String kind,String category,Set<String> dependencies,Set<String> conflicts){}
    public record FileEntry(String componentId,String path,String version,List<String> urls,String sha256,long size,String policy){ public FileEntry { urls=List.copyOf(urls); } }
    public record Server(String id,String name,String address){}
    public static Manifest parse(JsonObject j){
        Schema.validate("lock",j);
        Json.keys(j,"schemaVersion","project","release","minecraft","anthub","policies","servers","integrations","theme","components","files","content","extensions");
        if(j.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported schema");
        JsonObject p=j.getAsJsonObject("project"),r=j.getAsJsonObject("release"),m=j.getAsJsonObject("minecraft");
        if(!Json.str(m,"loader").equals("neoforge"))throw new IllegalArgumentException("Unsupported loader");
        String repo=Repositories.normalize(Json.str(p,"repository"));
        List<Component> cs=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(JsonElement e:j.getAsJsonArray("components")){
            JsonObject c=e.getAsJsonObject();Json.keys(c,"id","name","description","kind","category","dependencies","conflicts");
            String id=Json.str(c,"id"),kind=Json.str(c,"kind");slug(id);if(!ids.add(id)||!Set.of("required","recommended","optional").contains(kind))throw new IllegalArgumentException("Invalid component: "+id);
            cs.add(new Component(id,Json.str(c,"name"),kind,Json.opt(c,"category","other"),strings(c,"dependencies"),strings(c,"conflicts")));
        }
        List<FileEntry> fs=new ArrayList<>();Set<String> paths=new HashSet<>();
        for(JsonElement e:j.getAsJsonArray("files")){
            JsonObject f=e.getAsJsonObject();Json.keys(f,"componentId","path","version","urls","sha256","size","policy");
            String path=Json.str(f,"path"),hash=Json.str(f,"sha256"),policy=Json.str(f,"policy"),component=Json.str(f,"componentId");
            SafePaths.validate(path);Hashes.check(hash);
            long size=f.get("size").getAsBigDecimal().longValueExact();
            if(!paths.add(SafePaths.key(path))||!ids.contains(component)||size<0||size>8L*1024*1024*1024||!Set.of("enforce","update","preserve").contains(policy))throw new IllegalArgumentException("Invalid file: "+path);
            if(path.startsWith("mods/")&&!policy.equals("enforce"))throw new IllegalArgumentException("Mods must enforce");
            List<String> urls=new ArrayList<>();for(var source:f.getAsJsonArray("urls")){String url=source.getAsString();Remote.https(url);urls.add(url); }
            fs.add(new FileEntry(component,path,Json.str(f,"version"),urls,hash,size,policy));
        }
        List<Server> servers=new ArrayList<>();Set<String> serverIds=new HashSet<>();
        if(j.has("servers"))for(JsonElement e:j.getAsJsonArray("servers")){
            JsonObject v=e.getAsJsonObject();Json.keys(v,"id","name","address","description");String id=Json.str(v,"id");slug(id);
            if(!serverIds.add(id))throw new IllegalArgumentException("Duplicate server id");servers.add(new Server(id,Json.str(v,"name"),Json.str(v,"address")));
        }
        if(cs.size()>2000||fs.size()>10000||servers.size()!=1)throw new IllegalArgumentException("Manifest limits");
        Manifest result=new Manifest(j.deepCopy(),repo,Json.str(p,"id"),Json.str(p,"name"),Json.str(r,"version"),Json.str(m,"version"),Json.str(m,"loaderVersion"),Json.str(j.getAsJsonObject("anthub"),"minVersion"),List.copyOf(cs),List.copyOf(fs),List.copyOf(servers));
        new Selection(result).validate();return result;
    }
    public String projectKey(){return Hashes.sha256(repository.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public static void slug(String s){if(!s.matches("[a-z0-9][a-z0-9-]{0,63}"))throw new IllegalArgumentException("Invalid id: "+s);}
    private static Set<String> strings(JsonObject o,String key){Set<String>s=new LinkedHashSet<>();if(o.has(key))for(JsonElement e:o.getAsJsonArray(key)){String v=e.getAsString();if(!s.add(v))throw new IllegalArgumentException("Duplicate "+key);}return Set.copyOf(s);}
}
