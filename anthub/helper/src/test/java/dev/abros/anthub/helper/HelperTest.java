package dev.abros.anthub.helper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import dev.abros.anthub.core.*;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
class HelperTest {
    @TempDir Path game;
    @Test void helperWaitsForActualParentBeforeApplying()throws Exception{
        String hash=Hashes.sha256("new".getBytes());Path object=game.resolve("anthub/cache/objects/"+hash.substring(0,2)+"/"+hash);Files.createDirectories(object.getParent());Files.writeString(object,"new");var plan=new Planner.Plan(UUID.randomUUID().toString(),"test",List.of(new Planner.Change("mods/test.jar",null,hash)),Map.of(),Set.of(),List.of(),0);new Transactions(game).prepare(plan,new byte[0],new JsonObject());
        String java=Path.of(System.getProperty("java.home"),"bin","java").toString();String parentClasses=Path.of(Parent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();Process parent=new ProcessBuilder(java,"-cp",parentClasses,Parent.class.getName()).start();Process helper=null;
        try{
            String start=new BufferedReader(new InputStreamReader(parent.getInputStream())).readLine();helper=new ProcessBuilder(java,"-jar",System.getProperty("anthub.helperJar"),"apply",game.toString(),plan.id(),Long.toString(parent.pid()),start).redirectErrorStream(true).start();
            assertFalse(helper.waitFor(250,TimeUnit.MILLISECONDS));assertFalse(Files.exists(game.resolve("mods/test.jar")));parent.getOutputStream().write(1);parent.getOutputStream().flush();assertTrue(parent.waitFor(10,TimeUnit.SECONDS));assertTrue(helper.waitFor(10,TimeUnit.SECONDS));assertEquals(0,helper.exitValue(),new String(helper.getInputStream().readAllBytes()));assertEquals("new",Files.readString(game.resolve("mods/test.jar")));
        }finally{parent.destroyForcibly();if(helper!=null)helper.destroyForcibly();}
    }

    @Test void helperRenamesJarForUpgradeAndDowngradeAfterExit()throws Exception{
        for(String version:List.of("1.1.0","1.0.0")){
            Path old=game.resolve("mods/anthub-"+(version.equals("1.1.0")?"1.0.0":"1.1.0")+"-mc1.21.1-neoforge.jar");Files.createDirectories(old.getParent());if(!Files.exists(old))Files.writeString(old,"original");
            String destination="mods/anthub-"+version+"-mc1.21.1-neoforge.jar",hash=Hashes.sha256(version.getBytes());var cache=new Cache(game,new Remote());Files.createDirectories(cache.path(hash).getParent());Files.writeString(cache.path(hash),version);
            var plan=new Planner.Plan(UUID.randomUUID().toString(),"anthub-core",List.of(new Planner.Change(game.relativize(old).toString().replace('\\','/'),Hashes.sha256(old),null),new Planner.Change(destination,null,hash)),Map.of(),Set.of(),List.of(),0);
            new Transactions(game).prepare(plan,new byte[0],new JsonObject());String java=Path.of(System.getProperty("java.home"),"bin","java").toString(),classes=Path.of(Parent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            Process parent=new ProcessBuilder(java,"-cp",classes,Parent.class.getName()).start();Process helper=null;
            try{String start=new BufferedReader(new InputStreamReader(parent.getInputStream())).readLine();helper=new ProcessBuilder(java,"-jar",System.getProperty("anthub.helperJar"),"apply",game.toString(),plan.id(),Long.toString(parent.pid()),start).redirectErrorStream(true).start();
                assertFalse(helper.waitFor(250,TimeUnit.MILLISECONDS));assertTrue(Files.exists(old));assertFalse(Files.exists(game.resolve(destination)));
                parent.getOutputStream().write(1);parent.getOutputStream().flush();assertTrue(parent.waitFor(10,TimeUnit.SECONDS));assertTrue(helper.waitFor(10,TimeUnit.SECONDS));assertEquals(0,helper.exitValue(),new String(helper.getInputStream().readAllBytes()));
                assertFalse(Files.exists(old));assertEquals(version,Files.readString(game.resolve(destination)));assertFalse(Files.exists(game.resolve("anthub/pending.json")));
            }finally{parent.destroyForcibly();if(helper!=null)helper.destroyForcibly();}
        }
    }
}
