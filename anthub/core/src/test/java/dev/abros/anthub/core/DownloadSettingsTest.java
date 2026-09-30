package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DownloadSettingsTest{
 @Test void preferencesAreBoundedAndUseExactHosts(){assertThrows(IllegalArgumentException.class,()->new DownloadSettings(5,0,"auto"));assertThrows(IllegalArgumentException.class,()->new DownloadSettings(1,-1,"auto"));var settings=new DownloadSettings(2,5,"modrinth");assertTrue(settings.preferred("https://cdn.modrinth.com/data/file.jar"));assertFalse(settings.preferred("https://cdn.modrinth.com.evil.example/file.jar"));assertFalse(settings.preferred("https://edge.forgecdn.net/file.jar"));}
}
