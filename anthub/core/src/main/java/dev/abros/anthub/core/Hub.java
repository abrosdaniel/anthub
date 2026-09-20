package dev.abros.anthub.core;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
public final class Hub {
    public final SharedRequests requests=new SharedRequests();
    public final Path game;public final Remote remote=new Remote();public final RepositoryClient repositories;public final Cache cache;public final ProjectDetails details;
    private String recovery="";private boolean unreadableState,unreadablePreferences;
    public String recoveryMessage(){return recovery;}
    private void writableState()throws IOException{if(unreadableState)throw new IOException(recovery);}
    private Manifest loadedManifest;private Process helperProcess;private String helperTransaction="";
    private final Object coreCatalogLock=new Object(),coreInstallLock=new Object();
    private java.util.List<CoreUpdater.Update> coreCatalog;private long coreCatalogAt;
    private JsonObject preferences,state;private final String coreVersion,neoVersion;
    public Hub(Path game,String coreVersion,String neoVersion)throws IOException{
        this.game=game.toRealPath();new Transactions(game);this.coreVersion=coreVersion;this.neoVersion=neoVersion;
        remote.cacheMetadata(game.resolve("anthub/cache/http"));repositories=new RepositoryClient(game,remote);cache=new Cache(game,remote);
        details=new ProjectDetails(game,remote);
        try{preferences=load("preferences.json");}catch(IOException|RuntimeException bad){preferences=new JsonObject();unreadablePreferences=true;recovery="Не удалось прочитать настройки проектов. Каталог доступен; восстановите anthub/preferences.json из резервной копии перед сохранением изменений.";backup("preferences.json");}
        for(String repo:saved())details.load(repo);try{state=load("state.json");}catch(IOException|RuntimeException bad){state=new JsonObject();unreadableState=true;recovery="Не удалось прочитать anthub/state.json. Файл сохранён; установка заблокирована, чтобы не потерять учёт файлов. Восстановите его из резервной копии.";}
        if(state.has("lock"))try{loadedManifest=Manifest.parse(state.getAsJsonObject("lock"));}catch(RuntimeException bad){recovery="Сохранённый манифест проекта несовместим или повреждён. Откройте проект и нажмите «Обновить»: учёт установленных файлов сохранён.";}
        if(!recovery.isEmpty())backup("state.json");
        if(!unreadableState)try{owned();}catch(IOException|RuntimeException bad){unreadableState=true;recovery="Повреждён учёт установленных файлов. Каталог доступен; восстановите anthub/state.json из резервной копии перед изменением сборки.";backup("state.json");}

        String remove=Json.opt(preferences,"removeAfterDeactivation","");if(recovery.isEmpty()&&!remove.isEmpty()&&(active()==null||!active().repository().equals(remove))){removeRepository(remove);preferences.remove("removeAfterDeactivation");persist();}
    }
    private void backup(String name)throws IOException{
        Path source=game.resolve("anthub").resolve(name);if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))return;
        Path backup=game.resolve("anthub/recovery").resolve(name+"-"+Hashes.sha256(source)+".json");
        Files.createDirectories(backup.getParent());if(!Files.exists(backup))Files.copy(source,backup);
    }
    private JsonObject load(String name)throws IOException{Path p=game.resolve("anthub").resolve(name);return Files.exists(p)?Json.read(p):new JsonObject();}
    public synchronized JsonObject state(){return state.deepCopy();}
    public synchronized Manifest active(){return loadedManifest;}
    public synchronized String activeHash(){return Json.opt(state,"lockSha256","");}
    public synchronized List<String> saved(){List<String> result=new ArrayList<>();if(preferences.has("repositories")&&preferences.get("repositories").isJsonArray())for(var e:preferences.getAsJsonArray("repositories"))try{result.add(Repositories.normalize(e.getAsString()));}catch(RuntimeException ignored){/* One invalid repository must not block the catalog. */}return result;}
    public synchronized void saveRepository(String repo)throws IOException{List<String> values=new ArrayList<>(saved());repo=Repositories.normalize(repo);if(!values.contains(repo))values.add(repo);preferences.add("repositories",Json.GSON.toJsonTree(values));persist();}
    public synchronized void removeRepository(String repo)throws IOException{if(active()!=null&&active().repository().equals(repo))throw new IOException("Deactivate the active project before removing it");List<String> values=new ArrayList<>(saved());values.remove(repo);if(repo.equals(Json.opt(preferences,"selectedRepository",""))){preferences.remove("selectedRepository");preferences.remove("selectedName");preferences.remove("selectedVersion");}preferences.add("repositories",Json.GSON.toJsonTree(values));persist();}
    public synchronized void removeAfterDeactivation(String repo)throws IOException{preferences.addProperty("removeAfterDeactivation",repo);persist();}
    private void persist()throws IOException{if(unreadablePreferences)throw new IOException(recovery);Json.write(game.resolve("anthub/preferences.json"),preferences);}
    public synchronized Manifest.Server selectedServer(Manifest m){
        if(m.servers().isEmpty())return null;var original=m.servers().getFirst();var current=details.get(m.repository());
        return current==null?original:new Manifest.Server(original.id(),current.name(),current.address());
    }

    public String incompatibility(Manifest m){if(!m.minecraft().equals("1.21.1"))return "Minecraft "+m.minecraft();if(!m.neoForge().equals(neoVersion))return "NeoForge "+m.neoForge();if(!Versions.supportsBranch(coreVersion,m.anthubVersion()))return "AntHub "+m.anthubVersion();return "";}
    private synchronized Map<String,Planner.Owned> owned()throws IOException{writableState();Map<String,Planner.Owned> result=state.has("ownership")?Json.GSON.fromJson(state.get("ownership"),new TypeToken<Map<String,Planner.Owned>>(){}.getType()):Map.of();
        if(result==null)throw new IOException("Invalid ownership");
        for(var entry:result.entrySet()){SafePaths.resolve(game,entry.getKey());var value=entry.getValue();if(value==null||value.hash()==null||!Set.of("enforce","preserve","update").contains(value.policy()))throw new IOException("Invalid ownership");Hashes.check(value.hash());}return result;}
    private boolean sameSavedProject(Manifest manifest){
        if(active()!=null)return active().projectKey().equals(manifest.projectKey());
        try{return state.has("lock")&&Repositories.normalize(Json.str(state.getAsJsonObject("lock").getAsJsonObject("project"),"repository")).equals(manifest.repository());}catch(RuntimeException ignored){return manifest.projectKey().equals(Json.opt(state,"projectKey",""));}
    }
    public synchronized Set<String> choices(Manifest m){
        var selection=new Selection(m);
        if(state.has("selection")&&sameSavedProject(m)){
            try{Set<String> saved=Json.GSON.fromJson(state.get("selection"),new TypeToken<Set<String>>(){}.getType());
            if(saved!=null)return selection.restore(saved);}catch(JsonParseException|IllegalStateException bad){throw new IllegalArgumentException("Повреждён сохранённый выбор компонентов; восстановите состояние проекта",bad);}
        }
        Set<String> saved=null;
        try{
            Path path=game.resolve("anthub/projects").resolve(m.projectKey()).resolve("local-state.json");
            if(Files.exists(path))saved=Json.GSON.fromJson(Json.read(path).get("selection"),new TypeToken<Set<String>>(){}.getType());
        }catch(IOException|com.google.gson.JsonParseException ignored){}
        return saved==null?selection.initial():selection.restore(saved);
    }
    public Planner.Plan plan(RepositoryClient.Release release,Set<String> selection)throws IOException{return plan(release,selection,false);}
    public Planner.Plan plan(RepositoryClient.Release release,Set<String> selection,boolean backupModified)throws IOException{String incompatible=incompatibility(release.manifest());if(!incompatible.isEmpty())throw new IOException("Requires "+incompatible);return Planner.plan(game,withLocalConfig(release.manifest()),selection,owned(),cache,backupModified);}

    public Planner.Plan plan(RepositoryClient.Release release,Set<String> selection,Set<String> approved)throws IOException{
        String incompatible=incompatibility(release.manifest());if(!incompatible.isEmpty())throw new IOException("Requires "+incompatible);
        return Planner.plan(game,withLocalConfig(release.manifest()),selection,owned(),cache,true,Set.copyOf(approved));
    }
    public Planner.Plan plan(RepositoryClient.Release release,Set<String> selection,Set<String> approved,Set<String> kept)throws IOException{
        String incompatible=incompatibility(release.manifest());if(!incompatible.isEmpty())throw new IOException("Requires "+incompatible);
        var local=withLocalConfig(release.manifest());var original=new HashMap<String,Manifest.FileEntry>();for(var f:release.manifest().files())original.put(f.path(),f);
        var files=local.files().stream().map(f->approved.contains(f.path())?original.get(f.path()):f).toList();
        var target=new Manifest(local.json(),local.repository(),local.id(),local.name(),local.version(),local.minecraft(),local.neoForge(),local.anthubVersion(),local.components(),files,local.servers());
        return Planner.plan(game,target,selection,owned(),cache,true,approved,kept);
    }
    public String coreVersion(){return coreVersion;}
    public String neoVersion(){return neoVersion;}
    public synchronized void rememberProject(Manifest manifest)throws IOException{saveRepository(manifest.repository());preferences.addProperty("name-"+manifest.projectKey(),manifest.name());preferences.addProperty("selectedRepository",manifest.repository());preferences.addProperty("selectedName",manifest.name());preferences.addProperty("selectedVersion",manifest.version());persist();}
    public synchronized String projectLabel(String repo){var current=details.get(repo);if(current!=null)return current.name();return Json.opt(preferences,"name-"+Hashes.sha256(repo.getBytes(StandardCharsets.UTF_8)),repo.substring(repo.lastIndexOf('/')+1));}
    public synchronized String selectedRepository(){return Json.opt(preferences,"selectedRepository","");}
    public synchronized String menuProjectName(){return active()!=null?projectName(active()):Json.opt(preferences,"selectedName","");}
    public String projectName(Manifest manifest){var current=details.get(manifest.repository());return current==null?manifest.name():current.name();}
    public synchronized String menuProjectVersion(){return active()!=null?active().version():Json.opt(preferences,"selectedVersion","");}
    private void saveLocalState()throws IOException{
        if(active()==null)return;JsonObject saved=state.deepCopy();JsonObject configs=new JsonObject();
        for(var e:owned().entrySet())if(!e.getValue().policy().equals("enforce")){
            Path file=SafePaths.resolve(game,e.getKey());if(!Files.isRegularFile(file))continue;String hash=Hashes.sha256(file);Path object=cache.path(hash);Files.createDirectories(object.getParent());Files.copy(file,object,StandardCopyOption.REPLACE_EXISTING);configs.addProperty(e.getKey(),hash);
        }
        saved.add("localConfigs",configs);Json.write(game.resolve("anthub/projects").resolve(active().projectKey()).resolve("local-state.json"),saved);
    }
    private Manifest withLocalConfig(Manifest target)throws IOException{
        if(active()!=null&&active().projectKey().equals(target.projectKey()))return target;
        Path savedPath=game.resolve("anthub/projects").resolve(target.projectKey()).resolve("local-state.json");if(!Files.exists(savedPath))return target;JsonObject saved=Json.read(savedPath);if(!saved.has("localConfigs"))return target;
        var configs=saved.getAsJsonObject("localConfigs");List<Manifest.FileEntry> files=new ArrayList<>();for(var f:target.files()){
            if(!f.policy().equals("enforce")&&configs.has(f.path())){String hash=configs.get(f.path()).getAsString();if(!cache.contains(hash))throw new IOException("Saved project configuration missing from cache: "+f.path());files.add(new Manifest.FileEntry(f.componentId(),f.path(),f.version(),f.urls(),hash,Files.size(cache.path(hash)),"preserve"));}else files.add(f);
        }return new Manifest(target.json(),target.repository(),target.id(),target.name(),target.version(),target.minecraft(),target.neoForge(),target.anthubVersion(),target.components(),files,target.servers());
    }
    public synchronized String deactivate()throws Exception{writableState();
        if(active()==null)throw new IOException("No active project");if(helperProcess!=null&&helperProcess.isAlive())throw new IOException("An update is already pending");saveLocalState();List<Planner.Change> changes=new ArrayList<>();
        for(var e:owned().entrySet()){String current=Planner.hash(SafePaths.resolve(game,e.getKey()));if(current!=null)changes.add(new Planner.Change(e.getKey(),current,null));}
        String id=UUID.randomUUID().toString();var plan=new Planner.Plan(id,"deactivate",changes,Map.of(),Set.of(),List.of(),0);new Transactions(game).prepare(plan,new byte[0],new JsonObject());startHelper(id);return id;
    }
    public synchronized void pendingConnection(Manifest manifest,Manifest.Server server,String targetHash)throws IOException{
        Json.write(game.resolve("anthub/pending-connection.json"),Map.of("projectKey",manifest.projectKey(),"serverId",server.id(),"address",server.address(),"targetHash",targetHash,"createdAt",System.currentTimeMillis()));
    }
    public synchronized Manifest.Server consumePendingConnection()throws IOException{
        Path p=game.resolve("anthub/pending-connection.json");if(!Files.exists(p))return null;JsonObject intent=Json.read(p);Files.delete(p);
        if(active()==null||System.currentTimeMillis()-intent.get("createdAt").getAsLong()>86400000L||!active().projectKey().equals(Json.str(intent,"projectKey"))||!activeHash().equals(Json.str(intent,"targetHash"))||!audit().isEmpty())return null;
        var current=selectedServer(active());return current!=null&&current.id().equals(Json.str(intent,"serverId"))&&current.address().equals(Json.str(intent,"address"))?current:null;
    }
    public String stage(RepositoryClient.Release release,Planner.Plan plan,AtomicBoolean cancel,Consumer<String> progress)throws Exception{writableState();
        if(helperProcess!=null&&helperProcess.isAlive())throw new IOException("An update is already pending; close Minecraft first");
        if(!plan.conflicts().isEmpty())throw new IOException(String.join("\n",plan.conflicts()));
        repositories.trust(release);saveRepository(release.manifest().repository());saveLocalState();
        Set<String> needed=new HashSet<>();for(var c:plan.changes())if(c.after()!=null)needed.add(c.after());
        long required=plan.downloadBytes();for(var c:plan.changes())if(c.before()!=null)required+=Files.size(SafePaths.resolve(game,c.path()));
        if(Files.getFileStore(game).getUsableSpace()<required*2+16*1024*1024)throw new IOException("INSUFFICIENT_SPACE");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(4);var abort=new AtomicBoolean();
        try{java.util.List<java.util.concurrent.Future<?>> jobs=new java.util.ArrayList<>();
        var completed=new java.util.concurrent.ExecutorCompletionService<Void>(pool);
        java.util.Map<String,Manifest.FileEntry> unique=new java.util.LinkedHashMap<>();for(var f:release.manifest().files())if(needed.contains(f.sha256()))unique.put(f.sha256(),f);
        var meter=new DownloadProgress(unique.values().stream().mapToLong(Manifest.FileEntry::size).sum(),progress);
        for(var f:unique.values())jobs.add(completed.submit(()->{
            if(cancel.get()||abort.get())throw new IOException("Cancelled");
            cache.obtain(f,cancel,index->{meter.source(f.path(),index+1);},(position,received)->meter.position(f.path(),f.size(),position,received));meter.verified(f.path(),f.size());return null;
        }));
        try{for(int remaining=jobs.size();remaining>0;){
            if(cancel.get())throw new IOException("Cancelled");
            var job=completed.poll(100,java.util.concurrent.TimeUnit.MILLISECONDS);if(job!=null){job.get();remaining--;}
        }}catch(Exception failure){abort.set(true);for(var job:jobs)job.cancel(true);throw failure;}
        }finally{pool.shutdownNow();pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);}
        if(cancel.get())throw new IOException("Cancelled");
        JsonObject next=new JsonObject();next.addProperty("projectKey",release.manifest().projectKey());next.add("lock",release.manifest().json());next.addProperty("lockSha256",release.hash());next.addProperty("transactionId",plan.id());next.addProperty("installedAt",java.time.Instant.now().toString());next.add("ownership",Json.GSON.toJsonTree(plan.ownership()));next.add("selection",Json.GSON.toJsonTree(plan.selection()));
        if(plan.changes().isEmpty()){synchronized(this){
            if(Files.exists(game.resolve("anthub/pending.json")))throw new IOException("An update is already pending");
            for(var entry:plan.ownership().entrySet())if(entry.getValue().policy().equals("enforce")&&!entry.getValue().hash().equals(Planner.hash(SafePaths.resolve(game,entry.getKey()))))throw new IOException("Files changed during verification");
            JsonObject current=load("state.json");if(!current.equals(state))throw new IOException("Project state changed during verification");
            if(state.has("transactionId"))next.add("transactionId",state.get("transactionId"));else next.remove("transactionId");
            Json.write(game.resolve("anthub/state.json"),next);state=next;loadedManifest=release.manifest();recovery="";try{InstallationHistory.live(game,release.manifest().repository(),release.manifest().version());}catch(IOException logFailure){System.getLogger(Hub.class.getName()).log(System.Logger.Level.WARNING,"Could not record installation history",logFailure);}progress.accept("Applied without restart");return "";
        }}
        new Transactions(game).prepare(plan,release.bytes(),next);startHelper(plan.id());progress.accept("Ready to close Minecraft");return plan.id();
    }
    public List<String> audit()throws IOException{
        Set<String> optionalPaths=new HashSet<>();Manifest manifest=active();
        if(manifest!=null){Set<String> required=new Selection(manifest).resolve(Set.of());for(var file:manifest.files())if(!required.contains(file.componentId()))optionalPaths.add(file.path());}
        List<String> issues=new ArrayList<>();for(var e:owned().entrySet()){String hash=Planner.hash(SafePaths.resolve(game,e.getKey()));if(hash==null?!optionalPaths.contains(e.getKey()):!hash.equals(e.getValue().hash())&&e.getValue().policy().equals("enforce"))issues.add(e.getKey());}return issues;
    }
    public List<String> foreignMods()throws IOException{Path mods=game.resolve("mods");if(!Files.isDirectory(mods))return List.of();Set<String> paths=owned().keySet();try(var files=Files.list(mods)){return files.filter(p->p.getFileName().toString().endsWith(".jar")&&!paths.contains("mods/"+p.getFileName())).map(p->p.getFileName().toString()).toList();}}
    public RepositoryClient.Release installedRelease()throws Exception{
        Manifest m=active();if(m==null)throw new IOException("No active project");return repositories.snapshot(m.repository(),activeHash());
    }

    public long clearUnusedCache()throws IOException{
        Set<String> protectedHashes=new HashSet<>();Path data=game.resolve("anthub");
        try(var files=Files.walk(data.resolve("transactions"))){for(Path p:files.filter(p->p.getFileName().toString().equals("plan.json")).toList()){
            Path journal=p.resolveSibling("journal.json");if(Files.exists(journal)&&Set.of("COMMITTED","CANCELLED","ROLLED_BACK").contains(Json.str(Json.read(journal),"status")))continue;
            var plan=Json.GSON.fromJson(Json.read(p),Planner.Plan.class);for(var c:plan.changes()){if(c.before()!=null)protectedHashes.add(c.before());if(c.after()!=null)protectedHashes.add(c.after());}
        }}catch(NoSuchFileException ignored){}
        Set<String> savedKeys=new HashSet<>();for(String repo:saved())savedKeys.add(Hashes.sha256(repo.getBytes(StandardCharsets.UTF_8)));Path projects=data.resolve("projects");if(Files.isDirectory(projects))try(var snapshots=Files.walk(projects)){for(Path p:snapshots.filter(p->p.getFileName().toString().equals("local-state.json")).toList()){if(!savedKeys.contains(p.getParent().getFileName().toString()))continue;var saved=Json.read(p);if(saved.has("ownership"))for(var value:saved.getAsJsonObject("ownership").asMap().values())protectedHashes.add(Json.str(value.getAsJsonObject(),"hash"));if(saved.has("localConfigs"))for(var hash:saved.getAsJsonObject("localConfigs").asMap().values())protectedHashes.add(hash.getAsString());}}
        for(var e:owned().values())protectedHashes.add(e.hash());return cache.clearUnused(protectedHashes);
    }

    public synchronized Process startHelper(String transactionId)throws Exception{
        if(helperProcess!=null&&helperProcess.isAlive()){if(!helperTransaction.equals(transactionId))throw new IOException("A different update is already pending");return helperProcess;}
        Path runtime=game.resolve("anthub/runtime");Files.createDirectories(runtime);Path helper=runtime.resolve("helper.jar");
        try(InputStream in=Hub.class.getResourceAsStream("/anthub/helper.jar")){if(in==null)throw new IOException("Embedded helper missing");Files.copy(in,helper,StandardCopyOption.REPLACE_EXISTING);}
        String javaExecutable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        Process probe=new ProcessBuilder(javaExecutable,"-jar",helper.toString(),"--version").redirectErrorStream(true).start();if(!probe.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)||probe.exitValue()!=0)throw new IOException("HELPER_UNAVAILABLE");
        var self=ProcessHandle.current();helperTransaction=transactionId;helperProcess=new ProcessBuilder(javaExecutable,"-jar",helper.toString(),"apply",game.toString(),transactionId,Long.toString(self.pid()),self.info().startInstant().orElseThrow().toString()).redirectOutput(runtime.resolve("helper.log").toFile()).redirectErrorStream(true).start();return helperProcess;
    }
    /** Checking never downloads a JAR, prepares a transaction or starts a helper. */
    public java.util.Optional<CoreUpdater.Update> checkCoreUpdate()throws Exception{
        synchronized(this){if(Files.exists(game.resolve("anthub/pending.json"))||(helperProcess!=null&&helperProcess.isAlive()))return java.util.Optional.empty();}
        return new CoreUpdater(remote).check(coreVersion,"1.21.1",neoVersion);
    }
    public java.util.List<CoreUpdater.Update> availableCoreUpdates()throws Exception{
        synchronized(coreCatalogLock){
        if(coreCatalog!=null&&System.nanoTime()-coreCatalogAt<java.util.concurrent.TimeUnit.MINUTES.toNanos(5))return coreCatalog;
        var updates=new CoreUpdater(remote).releases(coreVersion,"1.21.1",neoVersion,100,true);coreCatalog=updates;coreCatalogAt=System.nanoTime();return updates;}
    }
    /** Called only after the player accepts this particular update. */
    public String prepareCoreUpdate(Path loadedJar,CoreUpdater.Update update)throws Exception{ synchronized(coreInstallLock){
        writableState();
        if(Files.exists(game.resolve("anthub/pending.json"))||(helperProcess!=null&&helperProcess.isAlive()))throw new IllegalStateException("Сначала завершите уже подготовленное обновление");
        String id=new CoreUpdater(remote).stage(game,loadedJar,update,cache,state());
        try { startHelper(id); }
        catch(Exception failure){if(helperProcess==null||!helperProcess.isAlive())new Transactions(game).abortReady(id);throw failure;}
        return id;}
    }
    public String content(Manifest m,String kind)throws Exception{StringBuilder result=new StringBuilder();
        for(var e:m.json().getAsJsonArray("content")){var c=e.getAsJsonObject();if(!kind.equals(Json.str(c,"type")))continue;String hash=Json.str(c,"sha256");Hashes.check(hash);Path p=game.resolve("anthub/cache/content").resolve(hash);byte[] b;
            b=Files.exists(p)?Files.readAllBytes(p):null;
            if(b==null||!Hashes.sha256(b).equals(hash)){
                b=remote.bytes(Json.str(c,"url"),1024*1024);if(!Hashes.sha256(b).equals(hash))throw new IOException("Content hash mismatch");
                Files.createDirectories(p.getParent());Path temp=Files.createTempFile(p.getParent(),"content-",".tmp");try{Files.write(temp,b);Json.move(temp,p);}finally{Files.deleteIfExists(temp);}
            }
            if(!Hashes.sha256(b).equals(hash))throw new IOException("Content hash mismatch");result.append("\n\n").append(new String(b,StandardCharsets.UTF_8));
        }return result.toString();
    }
}
