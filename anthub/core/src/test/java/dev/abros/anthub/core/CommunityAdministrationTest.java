package dev.abros.anthub.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class CommunityAdministrationTest {
 @TempDir Path temp;
 @Test void pinHasExpiryCanBeReplacedAndRemovedWithoutDeletingOtherRecords()throws Exception{
  var db=TestDatabase.database(temp);var store=new CommunityStore(db,CommunityStore.defaults());var admin=new CommunityAdministration(db,store);
  long before=System.currentTimeMillis();admin.pin("Operator","Встреча на спавне",1);
  assertEquals("Встреча на спавне",Json.str(admin.pin(),"text"));assertTrue(admin.pin().get("until").getAsLong()>=before+60000);
  admin.pin("Operator","Новое время",2);assertEquals("Новое время",Json.str(admin.pin(),"text"));
  admin.pin("Operator","",1);assertEquals("",Json.str(admin.pin(),"text"));
  assertThrows(IllegalArgumentException.class,()->admin.pin("Operator","Message",0));
 }
 @Test void exportIsCompactJsonLinesAndDoesNotIncludeAuthOrPrivateSystemRecords()throws Exception{
  var db=TestDatabase.database(temp);var store=new CommunityStore(db,CommunityStore.defaults());
  db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,?,?::jsonb)")){for(String section:List.of("board","groups","events","polls")){q.setString(1,UUID.randomUUID().toString());q.setString(2,section);q.setString(3,"{\"title\":\"Example\",\"description\":\"Line one\\nLine two\"}");q.executeUpdate();}}return null;});
  var secret=new JsonObject();secret.addProperty("token","NEVER_EXPORT_THIS_TOKEN");store.record("private-test","token",secret);
  Path file=new CommunityAdministration(db,store).export(temp.resolve("exports"),"Operator");
  var lines=Files.readAllLines(file);assertEquals(3,lines.size());Set<String> sections=new HashSet<>();for(String line:lines)sections.add(Json.str(Json.parse(line),"section"));assertEquals(Set.of("board","groups","events"),sections);assertFalse(Files.readString(file).contains("NEVER_EXPORT"));
  if(Files.getFileStore(file).supportsFileAttributeView("posix"))assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(file));
 }
}
