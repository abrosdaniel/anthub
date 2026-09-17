package dev.abros.anthub.core;
import com.google.gson.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import java.nio.file.*;import static org.junit.jupiter.api.Assertions.*;
class DraftStoreTest {
 @TempDir Path dir;
 @Test void draftsSurviveRestartAndArePartitioned()throws Exception{var db=new DraftStore(dir);var j=new JsonObject();j.addProperty("text","Привет");db.save("server","alice","reply:one",j);assertEquals(j,new DraftStore(dir).load("server","alice","reply:one"));assertTrue(db.load("other","alice","reply:one").isEmpty());assertTrue(db.load("server","bob","reply:one").isEmpty());assertTrue(db.load("server","alice","reply:two").isEmpty());db.remove("server","alice","reply:one");assertTrue(db.load("server","alice","reply:one").isEmpty());}
 @Test void secretsAndCommandTargetsCannotBePersisted(){var db=new DraftStore(dir);for(String key:new String[]{"password","token","target","operationId"}){var j=new JsonObject();j.addProperty(key,"sensitive");assertThrows(IllegalArgumentException.class,()->db.save("s","a","c",j));}}
}
