package dev.abros.anthub.core;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
public final class InstallationHistory {
 public record Entry(String at,String version,String status,List<Planner.Change> changes){}
 public static void live(Path game,String repository,String version)throws IOException{
  Path root=game.resolve("anthub/history");if(Files.isSymbolicLink(root))throw new IOException("Unsafe history directory");
  Json.write(root.resolve(UUID.randomUUID()+".json"),Map.of("repository",repository,"entry",new Entry(java.time.Instant.now().toString(),version,"COMMITTED",List.of())));
 }
 public static List<Entry> read(Path game,String repository)throws IOException{
  Path root=game.resolve("anthub/transactions");var result=new ArrayList<Entry>();
  Path history=game.resolve("anthub/history");if(Files.isDirectory(history)&&!Files.isSymbolicLink(history))try(var files=Files.list(history)){for(Path file:files.filter(p->p.getFileName().toString().endsWith(".json")&&!Files.isSymbolicLink(p)).toList()){try{var record=Json.read(file);if(repository.equals(Json.str(record,"repository"))){var entry=Json.GSON.fromJson(record.get("entry"),Entry.class);if(entry!=null&&entry.at()!=null&&entry.version()!=null&&entry.status()!=null&&entry.changes()!=null)result.add(entry);}}catch(IOException|RuntimeException ignored){}}}
  if(!Files.isDirectory(root)){result.sort(Comparator.comparing(Entry::at).reversed());return List.copyOf(result);}
  try(var dirs=Files.list(root)){for(Path dir:dirs.filter(Files::isDirectory).toList()){
   if(Files.isSymbolicLink(dir))continue;
   try{var next=Json.read(dir.resolve("next-state.json"));if(!next.has("lock"))continue;
    var lock=next.getAsJsonObject("lock");if(!repository.equals(Json.str(lock.getAsJsonObject("project"),"repository")))continue;
    var journal=Json.read(dir.resolve("journal.json"));var plan=Json.GSON.fromJson(Json.read(dir.resolve("plan.json")),Planner.Plan.class);
    result.add(new Entry(Json.opt(journal,"at",Files.getLastModifiedTime(dir.resolve("journal.json")).toInstant().toString()),Json.str(lock.getAsJsonObject("release"),"version"),Json.str(journal,"status"),plan.changes()));
   }catch(IOException|RuntimeException ignored){/* Interrupted preparation is not a completed installation. */}
  }}result.sort(Comparator.comparing(Entry::at).reversed());return List.copyOf(result);
 }
}
