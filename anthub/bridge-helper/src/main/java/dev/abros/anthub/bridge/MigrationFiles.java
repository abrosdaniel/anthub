package dev.abros.anthub.bridge;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;

public final class MigrationFiles {
    public static final String WORK = "anthub/rivet-migration";
    public static Path safe(Path root, String relative) throws IOException {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IOException("Unsafe migration path");
        for (Path p = path; p != null && !p.equals(root); p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Symbolic link in migration path: " + relative);
        return path;
    }
    public static String hash(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static boolean mod(Path jar, String id) throws IOException {
        try (var zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("META-INF/neoforge.mods.toml");
            if (entry == null) return false;
            try (var in = zip.getInputStream(entry)) {
                byte[] bytes = in.readNBytes(65537);
                if (bytes.length > 65536) throw new IOException("Oversized mod metadata");
                return java.util.regex.Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\"']" + id + "[\"']\\s*$")
                    .matcher(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).find();
            }
        }
    }
    public static void validateMods(Path game, Path source, String destination) throws IOException {
        Path mods = safe(game, "mods");
        if (!Files.isRegularFile(source) || !source.getParent().equals(mods) || !mod(source,"anthub"))
            throw new IOException("Install the transition JAR in this game's mods folder");
        if (Files.exists(safe(game, "mods/" + destination))) throw new IOException("Rivet destination already exists");
        try (var files = Files.list(mods)) {
            for (Path p : files.filter(p -> p.getFileName().toString().endsWith(".jar")).toList()) {
                if (p.equals(source)) continue;
                if (Files.isSymbolicLink(p)) throw new IOException("Symbolic link in mods folder");
                if (mod(p,"anthub") || mod(p,"rivet"))
                    throw new IOException("Another AntHub or Rivet JAR is already in mods: " + p.getFileName());
            }
        }
        if (Files.exists(safe(game,"rivet"))) throw new IOException("Rivet data already exists; automatic merging is disabled");
        if (Files.exists(safe(game,"anthub/pending.json"))) throw new IOException("Finish the previous AntHub update first");
    }
    public static void write(Path path, Properties values) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = Files.createTempFile(path.getParent(),"plan-",".tmp");
        try {
            try (var out = Files.newOutputStream(tmp)) { values.store(out,"AntHub to Rivet migration"); }
            try (var channel = java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
    }
    public static Properties read(Path path) throws IOException {
        var values = new Properties();
        try (var in = Files.newInputStream(path)) { values.load(in); }
        return values;
    }
    public static void copyClientData(Path game, Path staged, String version) throws IOException {
        Files.createDirectories(staged);
        for (String name : List.of("preferences.json","hud.json","accessibility.json","ui-theme.json",
                "home-layout.json","map-layers.json","menu-settings.json","server-list-ownership.json")) {
            Path source = safe(game,"anthub/"+name);
            if (Files.isRegularFile(source)) Files.copy(source,staged.resolve(name));
        }
        for (String directory : List.of("auth","drafts","projects","cache/objects")) {
            Path source = safe(game,"anthub/"+directory);
            if (!Files.isDirectory(source)) continue;
            try (var paths = Files.walk(source)) {
                for (Path path : paths.toList()) {
                    safe(game,game.relativize(path).toString());
                    Path target = staged.resolve(directory).resolve(source.relativize(path));
                    if (Files.isDirectory(path)) Files.createDirectories(target);
                    else if (Files.isRegularFile(path)) Files.copy(path,target);
                    else throw new IOException("Unsupported client data file");
                }
            }
        }
        Path previous = safe(game,"anthub/state.json");
        JsonObject state = new JsonObject();
        if (Files.exists(previous)) {
            if (Files.size(previous)>32*1024*1024) throw new IOException("Oversized client state");
            try (var reader=Files.newBufferedReader(previous)) { state=JsonParser.parseReader(reader).getAsJsonObject(); }
            // A renamed lock would no longer match its published checksum. Keep ownership and
            // selection, then let Rivet obtain a freshly published seed manifest.
            state.remove("lock"); state.remove("lockSha256"); state.remove("transactionId");
        }
        state.addProperty("coreVersion",version);
        Files.writeString(staged.resolve("state.json"),state.toString());
        // A migrated player has already chosen a Minecraft GUI scale; preserve it.
        Files.writeString(staged.resolve("client-defaults.json"),"{\"migratedFromAntHub\":true}");
    }
    public static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths=Files.walk(root)) {
            for (Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
