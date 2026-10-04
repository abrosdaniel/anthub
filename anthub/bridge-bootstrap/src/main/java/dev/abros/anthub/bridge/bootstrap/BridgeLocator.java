package dev.abros.anthub.bridge.bootstrap;
import net.neoforged.neoforgespi.locating.*;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.HexFormat;
public final class BridgeLocator implements IModFileCandidateLocator {
    private static final LaunchGate GATE=new LaunchGate();
    public int getPriority(){return Integer.MAX_VALUE;}
    public void findCandidates(ILaunchContext context,IDiscoveryPipeline pipeline){
        try{
            Path game=FMLPaths.GAMEDIR.get().toRealPath();recoverMigration(game);GATE.enter(game);
            byte[] embedded;try(InputStream in=getClass().getResourceAsStream("/anthub/game.jar")){if(in==null)throw new IOException("AntHub game module missing");embedded=in.readAllBytes();}
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(embedded));Path runtime=game.resolve("anthub/runtime");if(Files.isSymbolicLink(runtime))throw new IOException("Unsafe runtime directory");Files.createDirectories(runtime);
            Path module=runtime.resolve("game-"+hash+".jar");
            if(Files.isSymbolicLink(module))throw new IOException("Unsafe game module");
            if(!Files.exists(module)||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(module))).equals(hash)){
                Path tmp=Files.createTempFile(runtime,"game-",".tmp");try{Files.write(tmp,embedded);Files.move(tmp,module,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}
            }
            java.net.URI origin=getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            Path bundle=Path.of(origin);if(bundle.getFileSystem().provider().getScheme().equals("union"))bundle=(Path)bundle.getFileSystem().getClass().getMethod("getPrimaryPath").invoke(bundle.getFileSystem());
            // The outer bundle has launcher metadata, but only the extracted module is a game mod.
            context.addLocated(bundle.toAbsolutePath().normalize());
            System.setProperty("anthub.bundlePath",bundle.toAbsolutePath().toString());
            System.setProperty("anthub.launchGate","1");
            pipeline.addPath(module,ModFileDiscoveryAttributes.DEFAULT,IncompatibleFileReporting.ERROR);
        }catch(Exception e){throw new LinkageError("AntHub cannot safely start Minecraft: "+e.getMessage(),e);}
    }
    private static void recoverMigration(Path game)throws Exception {
        Path work=game.resolve("anthub/rivet-migration");
        for(Path p=work;p!=null&&!p.equals(game);p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Unsafe migration directory");
        Path pending=work.resolve("pending");
        if(!Files.exists(pending))return;
        Path helper=work.resolve("helper.jar");
        if(Files.isSymbolicLink(helper)||!Files.isRegularFile(helper))throw new IOException("Migration helper missing; restore AntHub from the backup");
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        Process recovery=new ProcessBuilder(executable,"-jar",helper.toString(),"recover",game.toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(work.resolve("helper.log").toFile())).start();
        if(!recovery.waitFor(30,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Rivet migration is waiting for another Minecraft process. Close all instances, wait for the installer, then start again. See anthub/rivet-migration/helper.log");
        if(recovery.exitValue()!=0)throw new IOException("Rivet migration needs attention. AntHub backup and details: anthub/rivet-migration/helper.log");
        throw new IOException("Rivet migration completed. Restart Minecraft to load Rivet.");
    }
}
