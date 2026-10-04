package dev.abros.anthub.bridge;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public final class MigrationHelper {
    public static void main(String[] args) throws Exception {
        if (args.length==1 && args[0].equals("--version")) { System.out.println("AntHub Rivet migration helper 1"); return; }
        if (args.length!=4 && args.length!=2) throw new IllegalArgumentException("apply GAME PID START | recover GAME");
        if (args[0].equals("apply") && args.length==4) {
            var parent=ProcessHandle.of(Long.parseLong(args[2]));
            if (parent.isPresent()) {
                var observed=parent.get().info().startInstant();
                if (parent.get().isAlive() && (observed.isEmpty() || !observed.get().equals(Instant.parse(args[3]))))
                    throw new IOException("Parent process identity changed");
                System.out.println("Waiting for Minecraft to exit"); System.out.flush();
                parent.get().onExit().get(24,TimeUnit.HOURS);
            }
        } else if (!args[0].equals("recover") || args.length!=2) throw new IllegalArgumentException("Invalid operation");
        Path game=Path.of(args[1]).toRealPath();
        Path lock=MigrationFiles.safe(game,"anthub/apply.lock");
        try (var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE)) {
            // Shared bootstrap locks cover every Minecraft process using this game directory.
            try (var exclusive=channel.lock()) { apply(game); }
        }
    }
    public static void apply(Path game) throws Exception {
        Path work=MigrationFiles.safe(game,MigrationFiles.WORK);
        Path planFile=MigrationFiles.safe(game,MigrationFiles.WORK+"/plan.properties");
        Properties plan=MigrationFiles.read(planFile);
        String old=plan.getProperty("old"), name=plan.getProperty("destination"), version=plan.getProperty("version");
        if (old==null || !old.matches("[^/\\\\]+\\.jar") || name==null || version==null ||
                !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || !name.equals("rivet-"+version+"-mc1.21.1-neoforge.jar"))
            throw new IOException("Invalid migration plan");
        String hash=plan.getProperty("sha256"),oldHash=plan.getProperty("oldHash");
        if (hash==null || !hash.matches("[0-9a-f]{64}") || oldHash==null || !oldHash.matches("[0-9a-f]{64}"))
            throw new IOException("Invalid migration hashes");
        Path original=MigrationFiles.safe(game,"mods/"+old), target=MigrationFiles.safe(game,"mods/"+name);
        Path artifact=MigrationFiles.safe(game,MigrationFiles.WORK+"/rivet.jar");
        Path backup=MigrationFiles.safe(game,MigrationFiles.WORK+"/original.jar");
        Path data=MigrationFiles.safe(game,"rivet"), staged=MigrationFiles.safe(game,MigrationFiles.WORK+"/data");
        if (!MigrationFiles.hash(artifact).equals(hash) || !MigrationFiles.mod(artifact,"rivet"))
            throw new IOException("Rivet verification failed");
        String phase=plan.getProperty("phase","ready");
        if (phase.equals("done")) {
            if (!Files.exists(target) || !MigrationFiles.hash(target).equals(hash)) throw new IOException("Completed Rivet install has changed");
            Files.deleteIfExists(work.resolve("pending")); return;
        }
        if (phase.equals("ready")) {
            MigrationFiles.validateMods(game,original,name);
            if (!MigrationFiles.hash(original).equals(oldHash)) throw new IOException("AntHub JAR changed after preparation");
            if (Files.exists(backup)) {
                if (!MigrationFiles.hash(backup).equals(oldHash)) throw new IOException("Backup mismatch");
            } else Files.copy(original,backup);
            MigrationFiles.deleteTree(staged);
            MigrationFiles.copyClientData(game,staged,version);
            phase(plan,planFile,"installing");
        } else if (!phase.equals("installing")) throw new IOException("Unknown migration phase");
        // Each step is atomic and can be resumed after a crash. AntHub is removed last.
        if (Files.exists(target)) {
            if (!MigrationFiles.hash(target).equals(hash)) throw new IOException("Existing Rivet JAR differs from staged file");
        } else {
            Path tmp=MigrationFiles.safe(game,MigrationFiles.WORK+"/install.jar");
            Files.copy(artifact,tmp,StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);
        }
        if (!Files.exists(data)) Files.move(staged,data,StandardCopyOption.ATOMIC_MOVE);
        else if (Files.exists(staged)) throw new IOException("Rivet data appeared during installation; refusing to overwrite");
        if (Files.exists(original)) {
            if (!MigrationFiles.hash(original).equals(oldHash) || !MigrationFiles.hash(backup).equals(oldHash))
                throw new IOException("AntHub changed; original will not be removed");
            Files.delete(original);
        } else if (!Files.exists(backup) || !MigrationFiles.hash(backup).equals(oldHash)) throw new IOException("AntHub backup missing");
        phase(plan,planFile,"done");
        Files.deleteIfExists(work.resolve("pending"));
        System.out.println("Rivet installed. Restart Minecraft. AntHub backup: "+backup);
    }
    private static void phase(Properties plan,Path file,String phase) throws IOException {
        plan.setProperty("phase",phase); MigrationFiles.write(file,plan);
    }
}
