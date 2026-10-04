package dev.abros.anthub.bridge;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import com.google.gson.*;

class MigrationTest {
    @TempDir Path game;
    Path work,old,target;
    Properties plan;
    @BeforeEach void setup()throws Exception{
        Files.createDirectories(game.resolve("mods"));
        work=game.resolve(MigrationFiles.WORK);Files.createDirectories(work);
        old=game.resolve("mods/anthub-3.9.0.jar");jar(old,"anthub");
        target=game.resolve("mods/rivet-1.0.0-mc1.21.1-neoforge.jar");
        jar(work.resolve("rivet.jar"),"rivet");
        plan=new Properties();plan.setProperty("old",old.getFileName().toString());plan.setProperty("oldHash",MigrationFiles.hash(old));
        plan.setProperty("destination",target.getFileName().toString());plan.setProperty("version","1.0.0");
        plan.setProperty("sha256",MigrationFiles.hash(work.resolve("rivet.jar")));plan.setProperty("phase","ready");
        save();Files.writeString(work.resolve("pending"),"ready");
    }
    void save()throws Exception{MigrationFiles.write(work.resolve("plan.properties"),plan);}
    static void jar(Path p,String id)throws Exception{
        try(var out=new ZipOutputStream(Files.newOutputStream(p))){out.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));out.write(("[[mods]]\nmodId=\""+id+"\"\n").getBytes());out.closeEntry();}
    }
    @Test void replacesOnlyAntHubAndPreservesClientDataAndOriginalBackup()throws Exception{
        Files.writeString(game.resolve("anthub/preferences.json"),"{\"repositories\":[\"example\"]}");
        Files.writeString(game.resolve("anthub/state.json"),"{\"lock\":{},\"lockSha256\":\"old\",\"ownership\":{},\"selection\":[\"x\"]}");
        Files.createDirectories(game.resolve("anthub/auth"));Files.writeString(game.resolve("anthub/auth/device.json"),"device-token");
        Path other=game.resolve("mods/other.jar");jar(other,"other");String otherHash=MigrationFiles.hash(other);
        MigrationHelper.apply(game);
        assertFalse(Files.exists(old));assertTrue(Files.exists(target));assertEquals(plan.getProperty("oldHash"),MigrationFiles.hash(work.resolve("original.jar")));
        assertEquals(otherHash,MigrationFiles.hash(other));assertEquals("device-token",Files.readString(game.resolve("rivet/auth/device.json")));
        var state=JsonParser.parseString(Files.readString(game.resolve("rivet/state.json"))).getAsJsonObject();
        assertFalse(state.has("lock"));assertFalse(state.has("lockSha256"));assertTrue(state.has("ownership"));assertTrue(state.has("selection"));
        assertTrue(Files.exists(game.resolve("anthub/auth/device.json")));assertFalse(Files.exists(work.resolve("pending")));
        MigrationHelper.apply(game); // Recovery after a completed transaction is harmless.
    }
    @Test void corruptedDownloadNeverDeletesAntHub()throws Exception{
        Files.writeString(work.resolve("rivet.jar"),"broken");assertThrows(Exception.class,()->MigrationHelper.apply(game));
        assertTrue(Files.exists(old));assertFalse(Files.exists(target));assertFalse(Files.exists(game.resolve("rivet")));
    }
    @Test void changedSourceNeverDeletesAntHub()throws Exception{
        Files.writeString(old,"changed");assertThrows(Exception.class,()->MigrationHelper.apply(game));assertEquals("changed",Files.readString(old));assertFalse(Files.exists(target));
    }
    @Test void existingRivetDataIsNeverMergedOrOverwritten()throws Exception{
        Files.createDirectories(game.resolve("rivet"));Files.writeString(game.resolve("rivet/keep"),"original");
        assertThrows(Exception.class,()->MigrationHelper.apply(game));assertEquals("original",Files.readString(game.resolve("rivet/keep")));assertTrue(Files.exists(old));
    }
    @Test void duplicateModIsRejectedEvenWhenRenamed()throws Exception{
        jar(game.resolve("mods/unrelated-name.jar"),"rivet");assertThrows(Exception.class,()->MigrationHelper.apply(game));assertTrue(Files.exists(old));
    }
    @Test void symlinkedDataIsRejected()throws Exception{
        Path outside=Files.createTempDirectory("bridge-external-");
        try{
            try{Files.createSymbolicLink(game.resolve("anthub/auth"),outside);}catch(java.io.IOException|UnsupportedOperationException unavailable){Assumptions.assumeTrue(false,"Symlinks unavailable");}
            assertThrows(Exception.class,()->MigrationHelper.apply(game));assertTrue(Files.exists(old));assertFalse(Files.exists(target));
        }finally{Files.deleteIfExists(game.resolve("anthub/auth"));Files.delete(outside);}
    }
    @Test void interruptedInstallationCanResume()throws Exception{
        Files.copy(old,work.resolve("original.jar"));MigrationFiles.copyClientData(game,work.resolve("data"),"1.0.0");
        Files.copy(work.resolve("rivet.jar"),target);plan.setProperty("phase","installing");save();
        MigrationHelper.apply(game);assertFalse(Files.exists(old));assertTrue(Files.exists(game.resolve("rivet/state.json")));assertFalse(Files.exists(work.resolve("pending")));
    }
    @Test void unexpectedTargetDuringRecoveryIsPreserved()throws Exception{
        Files.copy(old,work.resolve("original.jar"));MigrationFiles.copyClientData(game,work.resolve("data"),"1.0.0");
        Files.writeString(target,"user-file");plan.setProperty("phase","installing");save();
        assertThrows(Exception.class,()->MigrationHelper.apply(game));assertEquals("user-file",Files.readString(target));assertTrue(Files.exists(old));
    }
    @Test void pathTraversalIsRejected()throws Exception{
        plan.setProperty("old","../outside.jar");save();assertThrows(Exception.class,()->MigrationHelper.apply(game));assertTrue(Files.exists(old));
    }
    @Test void helperWaitsForParentBeforeReplacingLoadedJar()throws Exception{
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        var parent=new ProcessBuilder(java,"-cp",Path.of(Parent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),Parent.class.getName()).redirectErrorStream(true).start();
        String start=new java.io.BufferedReader(new java.io.InputStreamReader(parent.getInputStream())).readLine();
        var helper=new ProcessBuilder(java,"-jar",System.getProperty("bridge.helperJar"),"apply",game.toString(),Long.toString(parent.pid()),start)
            .redirectErrorStream(true).redirectOutput(work.resolve("test-helper.log").toFile()).start();
        try{
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(!Files.readString(work.resolve("test-helper.log")).contains("Waiting for Minecraft")&&helper.isAlive()&&System.nanoTime()<until)Thread.sleep(50);
            assertTrue(helper.isAlive(),Files.readString(work.resolve("test-helper.log")));assertTrue(Files.exists(old));assertFalse(Files.exists(target));
            parent.getOutputStream().close();assertTrue(parent.waitFor(10,TimeUnit.SECONDS));assertTrue(helper.waitFor(20,TimeUnit.SECONDS));
            assertEquals(0,helper.exitValue(),Files.readString(work.resolve("test-helper.log")));assertFalse(Files.exists(old));assertTrue(Files.exists(target));
        }finally{parent.destroyForcibly();helper.destroyForcibly();}
    }
}
