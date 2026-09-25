package dev.abros.anthub.core;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Local, bounded non-auth form drafts. Server/account/context are all part of the key. */
public final class DraftStore {
 private final Path directory;
 public DraftStore(Path directory){this.directory=directory;}
 private Path path(String server,String account,String context){return directory.resolve(Hashes.sha256((server+"\0"+account+"\0"+context).getBytes(StandardCharsets.UTF_8))+".json");}
 public synchronized JsonObject load(String server,String account,String context)throws Exception{
  Path p=path(server,account,context);if(!Files.exists(p))return new JsonObject();
  if(Files.size(p)>65536||Files.getLastModifiedTime(p).toMillis()<System.currentTimeMillis()-30L*86400000){Files.delete(p);return new JsonObject();}
  return Json.read(p);
 }
 public synchronized void save(String server,String account,String context,JsonObject fields)throws Exception{
  if(Json.GSON.toJson(fields).length()>16000)throw new IllegalArgumentException("Draft too large");
  for(String key:fields.keySet())if(!Set.of("title","description","text","days","type","category","group","startsAt","endsAt","capacity","options","multiple","changeVote","liveResults","location","playerNames","clearLocation","visibility","invitees","repeat","occurrences","timezone","trade","item","quantity","terms","assignee","dueAt","reason","dimension","x","y","z","membersOnly").contains(key))throw new IllegalArgumentException("Not a draft field");
  Json.write(path(server,account,context),fields);
  try(var files=Files.list(directory)){var paths=files.filter(p->p.getFileName().toString().matches("[a-f0-9]{64}\\.json")).sorted(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p).toMillis();}catch(Exception e){return 0L;}})).toList();for(int i=0;i<paths.size()-100;i++)Files.deleteIfExists(paths.get(i));}
 }
 public synchronized JsonObject pending(String server,String account,String context)throws Exception{Path p=pendingPath(server,account,context);if(!Files.exists(p))return null;if(Files.size(p)>65536)throw new IllegalArgumentException("Pending command too large");if(Files.getLastModifiedTime(p).toMillis()<System.currentTimeMillis()-7L*86400000){Files.delete(p);return null;}return Json.read(p);}
 public synchronized void pending(String server,String account,String context,JsonObject command)throws Exception{
  if(command==null){Files.deleteIfExists(pendingPath(server,account,context));return;}
  for(String key:command.keySet())if(!Set.of("title","description","text","days","type","category","group","startsAt","endsAt","capacity","options","multiple","changeVote","liveResults","location","target","decision","status","revision","operationId","issuedAt","kind","itemId","itemRevision","clearLocation","visibility","invitees","repeat","occurrences","timezone","trade","item","quantity","terms","assignee","dueAt","reason","dimension","x","y","z","membersOnly").contains(key))throw new IllegalArgumentException("Not a form command field");
  if(Json.GSON.toJson(command).length()>16000)throw new IllegalArgumentException("Pending command too large");Json.write(pendingPath(server,account,context),command);
  try(var files=Files.list(directory)){var pending=files.filter(p->p.getFileName().toString().endsWith(".json.pending")).sorted(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p).toMillis();}catch(Exception ex){return 0L;}})).toList();for(int i=0;i<pending.size()-100;i++)Files.deleteIfExists(pending.get(i));}
 }
 private Path pendingPath(String server,String account,String context){Path p=path(server,account,context);return p.resolveSibling(p.getFileName()+".pending");}
 public synchronized void remove(String server,String account,String context)throws Exception{Files.deleteIfExists(path(server,account,context));}
}
