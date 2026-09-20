package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ServerSettingsTest {
 @TempDir Path root;
 @Test void defaultsCoverAllModules()throws Exception{var s=ServerSettings.load(root);assertEquals("false",s.text("auth.mode"));assertEquals(6,s.number("auth.minimumPasswordLength"));assertFalse(s.votes().enabled());assertTrue(s.statistics().deaths());assertEquals(8,s.community().getAsJsonArray("sections").size());assertTrue(s.menu().getAsJsonArray("links").isEmpty());}
 @Test void acceptsLinksAndStatsSettings()throws Exception{var s=ServerSettings.parse(ServerSettings.template().replace("links = []","links = [{name = 'Сайт', url = 'https://example.org'}]").replace("totalPlayTime = true","totalPlayTime = false"));assertFalse(s.statistics().totalTime());assertEquals("Сайт",s.menu().getAsJsonArray("links").get(0).getAsJsonObject().get("name").getAsString());assertFalse(s.menu().has("database"));}
 @Test void rejectsOldFlatConfigWithoutRewriting()throws Exception{Path f=root.resolve("config/anthub-server.toml");Files.createDirectories(f.getParent());String old="project = 'abrosdaniel/example'\nluckperms = true\n";Files.writeString(f,old);assertThrows(IllegalArgumentException.class,()->ServerSettings.load(root));assertEquals(old,Files.readString(f));}
 @Test void rejectsMissingAuthAndUnknownKeys()throws Exception{String t=ServerSettings.template();assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("mode = \"false\"","")));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t+"\nunknown = true\n"));}
 @Test void rejectsInvalidTypesAndRanges()throws Exception{String t=ServerSettings.template();for(String invalid:new String[]{t.replace("mode = \"false\"","mode = false"),t.replace("minimumPlayers = 5","minimumPlayers = 4"),t.replace("minimumPlayers = 5","minimumPlayers = 5.5"),t.replace("minimumPasswordLength = 6","minimumPasswordLength = 5"),t.replace("poolSize = 8","poolSize = 99999999999"),t.replace("links = []","links = [{name='Bad',url='http://example.org'}]"),t.replace("categories = []","categories = [1]")})assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(invalid));}
 @Test void syntaxErrorsNeverLeakSecrets()throws Exception{var error=assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(ServerSettings.template().replace("password = \"\"","password = \"secret-marker")));assertFalse(error.toString().contains("secret-marker"));assertNull(error.getCause());}
 @Test void rejectsDuplicateKeys()throws Exception{assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(ServerSettings.template()+"\nnotifyConsole = false\n"));}
 @Test void rejectsSymlink()throws Exception{Path f=root.resolve("config/anthub-server.toml");Files.createDirectories(f.getParent());Path target=root.resolve("secret");Files.writeString(target,"unchanged");Files.createSymbolicLink(f,target);assertThrows(IllegalArgumentException.class,()->ServerSettings.load(root));assertEquals("unchanged",Files.readString(target));}
}
