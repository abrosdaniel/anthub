package dev.abros.anthub.core;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import com.google.gson.JsonObject;

/** Mutable display/connection details, separate from immutable installed pack manifests. */
public final class ProjectDetails {
    public record Details(String name, String address) {}
    private final Path root;
    private final Remote remote;
    private final java.util.function.LongSupplier clock;
    private final Map<String,Long> checked = new ConcurrentHashMap<>();
    private static final long FRESH_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(1);
    private final Map<String, Details> known = new ConcurrentHashMap<>();
    public ProjectDetails(Path game, Remote remote) { this(game,remote,System::nanoTime); }
    ProjectDetails(Path game, Remote remote, java.util.function.LongSupplier clock) { this.root=game.resolve("anthub/projects"); this.remote=remote; this.clock=clock; }
    private Path file(String repo) { return root.resolve(Hashes.sha256(repo.getBytes(StandardCharsets.UTF_8))).resolve("details.json"); }
    public Details get(String repo) { return known.get(Repositories.normalize(repo)); }
    public void load(String repository) {
        String repo=Repositories.normalize(repository);
        try { if(Files.exists(file(repo))) known.put(repo,parse(Json.read(file(repo)))); }
        catch(Exception ignored) { /* A damaged display cache is replaced on the next successful refresh. */ }
    }
    private static Details parse(JsonObject json) {
        String name=Json.str(json,"name"),address=Json.str(json.getAsJsonObject("server"),"address");
        if(name.isBlank()||name.length()>512||name.chars().anyMatch(Character::isISOControl)
            ||address.isBlank()||address.length()>512||address.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))
            ||address.contains("/")||address.contains("\\")||address.contains("@")||address.contains("#")||address.contains("?"))
            throw new IllegalArgumentException("Invalid project name or server address");
        return new Details(name,address);
    }
    /** Connection checks reuse a successful response for one minute; this never schedules requests. */
    public synchronized Details refreshIfStale(String repository) throws Exception {
        String repo=Repositories.normalize(repository);
        Long last=checked.get(repo);
        if(last!=null && clock.getAsLong()-last<FRESH_NANOS)return known.get(repo);
        return refresh(repo);
    }
    /** Explicit refresh bypasses the age check. Disk cache is never treated as freshly checked. */
    public synchronized Details refresh(String repository) throws Exception {
        String repo=Repositories.normalize(repository);
        if(!known.containsKey(repo))load(repo);
        JsonObject json;
        try { json=Json.parse(new String(remote.bytes(Repositories.raw(repo,"anthub.json"),8*1024*1024),StandardCharsets.UTF_8)); }
        catch(Remote.Unavailable|java.net.UnknownHostException offline) { return known.get(repo); }
        Schema.validate("project",json);
        Details details=parse(json);
        var cache=new JsonObject();cache.addProperty("name",details.name());var server=new JsonObject();server.addProperty("address",details.address());cache.add("server",server);
        Json.write(file(repo),cache);
        known.put(repo,details);
        checked.put(repo,clock.getAsLong());
        return details;
    }
}
