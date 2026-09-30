package dev.abros.anthub.core;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Server-only operations. Caller must recheck administrator permissions before entering. */
public final class CommunityAdministration {
 private final PgDatabase db;private final CommunityStore store;
 public CommunityAdministration(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 public JsonObject pin(String actor,String message,int minutes)throws Exception{
  if(message.length()>500||message.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n')||minutes<1||minutes>10080)throw new IllegalArgumentException("Сообщение: до 500 символов, срок: 1–10080 минут");
  return db.communityTransaction(()->{db.lock("community:pin");var pin=new JsonObject();pin.addProperty("text",message.strip());pin.addProperty("until",System.currentTimeMillis()+minutes*60000L);pin.addProperty("author",actor);store.record("state","pinned-announcement",pin);store.audit(actor,message.isBlank()?"announcement unpinned":"announcement pinned",message.strip());CommunityOutbox.add(db,"home","","");return pin;});
 }
 public JsonObject pin()throws Exception{return store.record("state","pinned-announcement");}
 /** Streaming snapshot of community documents only. Never reads Auth, devices, receipts or diagnostics. */
 public Path export(Path directory,String actor)throws Exception{return export(directory,actor,"");}
 public Path export(Path directory,String actor,String section)throws Exception{if(!section.isEmpty()&&!Set.of("board","groups","events","polls","ideas").contains(section))throw new IllegalArgumentException("Недоступный раздел экспорта");
  Files.createDirectories(directory);Path path=Files.createTempFile(directory,"community-",".jsonl");
  try{
   try{Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}
   db.communityTransaction(()->{
    try(var q=db.connection().prepareStatement("SELECT section,body FROM community_documents WHERE section IN ('board','groups','events','polls','ideas') AND (?='' OR section=?) ORDER BY sequence")){q.setString(1,section);q.setString(2,section);q.setFetchSize(100);
     try(var rows=q.executeQuery();var out=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){
      long bytes=0;while(rows.next()){var row=new JsonObject();row.addProperty("section",rows.getString(1));row.add("document",Json.parse(rows.getString(2)));String encoded=row.toString();bytes+=encoded.getBytes(StandardCharsets.UTF_8).length+1;if(bytes>64L*1024*1024)throw new IllegalStateException("Экспорт превышает 64 МиБ; используйте резервную копию PostgreSQL");out.write(encoded);out.newLine();}
     }
    }store.audit(actor,"community exported",path.getFileName().toString());return null;
   });try(var lines=Files.lines(path)){for(String line:(Iterable<String>)lines::iterator){var row=Json.parse(line);if(!row.has("section")||!row.has("document"))throw new IllegalStateException("Повреждённый экспорт");}}Files.writeString(path.resolveSibling(path.getFileName()+".sha256"),Hashes.sha256(path));return path;
  }catch(Exception failure){Files.deleteIfExists(path);throw failure;}
 }
}
