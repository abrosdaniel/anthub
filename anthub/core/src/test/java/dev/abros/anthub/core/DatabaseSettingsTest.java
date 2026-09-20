package dev.abros.anthub.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseSettingsTest {
    @TempDir Path game;
    void config(String password,String env)throws Exception {
        Path file=game.resolve("config/anthub-server.toml");Files.createDirectories(file.getParent());
        Files.writeString(file,ServerSettings.template().replace("password = \"\"","password = '"+password+"'").replace("passwordEnvironment = \"\"","passwordEnvironment = \""+env+"\""));
    }
    @Test void acceptsLiteralPasswordAndHidesIt()throws Exception {
        String password="sëcret=:#\\value with spaces";config(password,"");
        var settings=DatabaseSettings.load(game,key->{fail("Empty env must not be read");return null;});
        assertEquals(password,settings.password());assertFalse(settings.toString().contains(password));
    }
    @Test void environmentOverridesConfig()throws Exception {
        config("config-secret","ANTHUB_DB_PASSWORD");
        assertEquals("environment-secret",DatabaseSettings.load(game,key->{assertEquals("ANTHUB_DB_PASSWORD",key);return "environment-secret";}).password());
    }
    @Test void missingExplicitEnvironmentDoesNotSilentlyFallback()throws Exception {
        config("config-secret","ANTHUB_DB_PASSWORD");assertThrows(IllegalArgumentException.class,()->DatabaseSettings.load(game,key->null));
    }
    @Test void oldPasswordFileIsNotRead()throws Exception {
        config("","");Files.writeString(game.resolve("config/anthub-db.password"),"old-secret");
        assertThrows(IllegalArgumentException.class,()->DatabaseSettings.load(game,key->null));
    }
    @Test void noCredentialsFailsWithSetupHint()throws Exception {
        config("","");var error=assertThrows(IllegalArgumentException.class,()->DatabaseSettings.load(game,key->null));
        assertTrue(error.getMessage().contains("database.password"));
    }
    @Test void generatesOnlyUnifiedConfigWithoutCredentials()throws Exception {
        assertThrows(IllegalArgumentException.class,()->DatabaseSettings.load(game,key->null));
        assertTrue(Files.exists(game.resolve("config/anthub-server.toml")));
        assertFalse(Files.exists(game.resolve("config/anthub-database.properties")));
        assertEquals("verify-full",ServerSettings.load(game).text("database.sslMode"));
    }
}
