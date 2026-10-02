package dev.abros.anthub.core;

import com.google.gson.JsonObject;
import java.util.*;

/** Connection-scoped scheduling and read cache, independent of Minecraft and screen rendering. */
public final class MenuTransport<O> {
    private static final int CAPACITY = 64, BACKGROUND_CAPACITY = 48;
    private static final long READ_TIMEOUT = 15000, ACTION_INTERVAL = 550;
    private record Pending<O>(JsonObject packet, O owner, boolean read, long at) {}
    private record Read<O>(JsonObject packet, O owner, long at) {}
    private final ArrayDeque<Pending<O>> outbound = new ArrayDeque<>();
    private final Map<String, Long> dispatched = new LinkedHashMap<>();
    private final Map<String, Read<O>> reads = new LinkedHashMap<>();
    private final MenuReadCache cache = new MenuReadCache();

    public boolean enqueue(JsonObject packet, O owner, boolean background, long now) {
        var prepared = MenuCommands.prepare(packet, now);
        boolean read = MenuRequests.read(prepared);
        if (background && MenuRequests.mutation(prepared))
            throw new IllegalArgumentException("Background requests must not mutate data");
        if (read) outbound.removeIf(queued -> {
            boolean same = queued.read && queued.owner == owner && MenuRequests.sameRead(queued.packet, prepared);
            if (same) forget(queued.packet);
            return same;
        });
        if (Json.opt(prepared, "action", "").equals("subscribe"))
            outbound.removeIf(queued -> Json.opt(queued.packet, "action", "").equals("subscribe"));
        if (outbound.size() >= (background ? BACKGROUND_CAPACITY : CAPACITY)) return false;
        if (MenuRequests.mutation(prepared)) clearCache();
        // A slower, older reply for the same query must not overwrite a newer cached snapshot.
        if (read) reads.values().removeIf(previous -> previous.owner == owner && MenuRequests.sameRead(previous.packet, prepared));
        outbound.add(new Pending<>(prepared, owner, read, now));
        if (read && prepared.has("request")) {
            reads.put(Json.str(prepared, "request"), new Read<>(prepared.deepCopy(), owner, now));
            while (reads.size() > CAPACITY) reads.remove(reads.keySet().iterator().next());
        }
        return true;
    }
    public List<JsonObject> dispatch(long now, O currentOwner) {
        var ready = new ArrayList<JsonObject>();
        var iterator = outbound.iterator();
        while (iterator.hasNext()) {
            var queued = iterator.next();
            if (queued.read && (queued.owner != null && queued.owner != currentOwner || now - queued.at >= READ_TIMEOUT)) {
                iterator.remove(); forget(queued.packet); continue;
            }
            String action = Json.opt(queued.packet, "action", "");
            if (now - dispatched.getOrDefault(action, -ACTION_INTERVAL) < ACTION_INTERVAL) continue;
            iterator.remove(); dispatched.put(action, now);
            while (dispatched.size() > CAPACITY) dispatched.remove(dispatched.keySet().iterator().next());
            ready.add(queued.packet.deepCopy());
        }
        reads.values().removeIf(read -> now - read.at >= READ_TIMEOUT);
        return List.copyOf(ready);
    }
    public void receive(JsonObject reply, long now) {
        var original = reads.remove(Json.opt(reply, "request", ""));
        if (original != null && now - original.at < READ_TIMEOUT) cache.put(original.packet, reply, now);
    }
    public JsonObject cached(JsonObject packet, long now) { return cache.get(packet, now); }
    public void cancel(O owner) {
        outbound.removeIf(queued -> {
            if (queued.read && queued.owner == owner) { forget(queued.packet); return true; }
            return false;
        });
        reads.values().removeIf(read -> read.owner == owner);
    }
    public void invalidate(String section, String id) { reads.clear(); cache.invalidate(section, id); }
    public void clearCache() { cache.clear(); reads.clear(); }
    public void clear() { outbound.clear(); dispatched.clear(); clearCache(); }
    public int queued() { return outbound.size(); }
    public int trackedReads() { return reads.size(); }
    private void forget(JsonObject packet) { reads.remove(Json.opt(packet, "request", "")); }
}
