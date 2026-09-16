package dev.abros.anthub.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseSettingsTest {
    @TempDir Path game;
    void config(String password)throws Exception {
        var values=new Properties();values.setProperty("password",password);
        Path file=game.resolve("config/anthub-database.properties");Files.createDirectories(file.getParent());
        try(var writer=Files.newBufferedWriter(file)){values.store(writer,null);}
    }
    void secret(String password)throws Exception {
        Files.createDirectories(game.resolve("config"));Files.writeString(game.resolve("config/anthub-db.password"),password);
    }
    @Test void acceptsPasswordInConfigWithoutEnvironmentOrSecretFile()throws Exception {
        String password="sëcret=:#\\value with spaces";config(password);
        var settings=DatabaseSettings.load(game,key->null);
        assertEquals(password,settings.password());assertFalse(settings.toString().contains(password));
    }
    @Test void environmentOverridesConfigAndFile()throws Exception {
        config("config-secret");secret("file-secret");
        assertEquals("environment-secret",DatabaseSettings.load(game,key->{assertEquals("ANTHUB_DB_PASSWORD",key);return "environment-secret";}).password());
    }
    @Test void blankEnvironmentUsesConfigBeforeFile()throws Exception {
        config("config-secret");secret("file-secret");
        assertEquals("config-secret",DatabaseSettings.load(game,key->" ").password());
    }
    @Test void emptyConfigUsesPasswordFile()throws Exception {
        config("");secret("file-secret\n");
        assertEquals("file-secret",DatabaseSettings.load(game,key->null).password());
    }
    @Test void noCredentialsStillFailsWithSetupHint()throws Exception {
        config("");var error=assertThrows(IllegalStateException.class,()->DatabaseSettings.load(game,key->null));
        assertTrue(error.getMessage().contains("password"));
    }
    @Test void generatedConfigIncludesEmptyPasswordAndDoesNotInventCredentials()throws Exception {
        assertThrows(IllegalStateException.class,()->DatabaseSettings.load(game,key->null));
        var values=new Properties();try(var reader=Files.newBufferedReader(game.resolve("config/anthub-database.properties"))){values.load(reader);}
        assertEquals("",values.getProperty("password"));assertEquals("verify-full",values.getProperty("sslMode"));
    }
}
