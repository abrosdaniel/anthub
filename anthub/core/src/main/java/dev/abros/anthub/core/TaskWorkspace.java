package dev.abros.anthub.core;

import com.google.gson.*;
import java.util.Set;
import java.util.function.*;

/** Task application state. Rendering, Minecraft navigation and draft fields belong to the view. */
public final class TaskWorkspace {
    public record Outcome(boolean accepted, boolean changed, boolean saved, boolean conflict, boolean loadDetail) {
        private static final Outcome IGNORED = new Outcome(false, false, false, false, false);
    }
    private final String group;
    private final Consumer<JsonObject> sender;
    private final Function<JsonObject, JsonObject> cache;
    private final LongSupplier clock;
    private final RequestSession session = new RequestSession();
    private String id, notice = "", operation = "", request = "", cursor = "", nextCursor = "";
    private JsonObject data = new JsonObject(), undo;
    private JsonArray tasks = new JsonArray();
    private boolean busy, retry, listLoaded, canCreate, undoing, dirty, redrawAfterReply;
    private long undoUntil;

    public TaskWorkspace(String group, String id, Consumer<JsonObject> sender,
                         Function<JsonObject, JsonObject> cache, LongSupplier clock) {
        this.group = group; this.id = id; this.sender = sender; this.cache = cache; this.clock = clock;
    }
    public String id() { return id; }
    public String notice() { return notice; }
    public void notice(String value) { notice = value; }
    public String operation() { return operation; }
    public String cursor() { return cursor; }
    public void cursor(String value) { cursor = value; }
    public String nextCursor() { return nextCursor; }
    public boolean busy() { return busy; }
    public boolean retryAvailable() { return retry; }
    public boolean listLoaded() { return listLoaded; }
    public boolean canCreate() { return canCreate; }
    public JsonObject data() { return data.deepCopy(); }
    public JsonArray tasks() { return tasks.deepCopy(); }
    public boolean reading() { return Set.of("workList", "workGet").contains(operation); }
    public boolean matches(JsonObject reply) { return request.equals(Json.opt(reply, "request", "")); }
    public void invalidate(String section, String target) {
        if (section.equals(group.isEmpty() ? "home" : "groups") &&
            (group.isEmpty() || target.isEmpty() || target.equals(group))) dirty = true;
    }
    public boolean consumeDirty() { if (!dirty || busy || retry) return false; dirty = false; return true; }
    public void cancelRead() { if (busy && reading()) { session.cancel(); busy = false; dirty = true; } }
    public void close() { session.cancel(); busy = false; }
    public void clearSelection() { id = ""; data.remove("task"); undo = null; }
    public boolean select(String next) {
        if (busy && !reading() || next.equals(id) && data.has("task")) return false;
        session.cancel(); busy = false; id = next; data.remove("task"); undo = null; return true;
    }
    public JsonObject body() {
        var body = new JsonObject(); body.addProperty("group", group);
        if (id.isEmpty() && !cursor.isEmpty()) body.addProperty("cursor", cursor);
        if (!id.isEmpty()) {
            body.addProperty("task", id);
            if (data.has("task") && data.getAsJsonObject("task").has("revision"))
                body.add("revision", data.getAsJsonObject("task").get("revision").deepCopy());
        }
        return body;
    }
    public void list(String query, String status) {
        var body = new JsonObject(); body.addProperty("group", group); body.addProperty("query", query);
        body.addProperty("status", status); if (!cursor.isEmpty()) body.addProperty("cursor", cursor);
        send("workList", body);
    }
    public boolean load() {
        var body = body(); boolean cached = false;
        if (!id.isEmpty() && !data.has("task")) {
            var query = body.deepCopy(); query.addProperty("action", "community");
            query.addProperty("section", "home"); query.addProperty("op", "workGet");
            var snapshot = cache.apply(query);
            if (snapshot != null && snapshot.has("task")) { data.add("task", snapshot.get("task").deepCopy()); cached = true; }
        }
        send(id.isEmpty() ? "workList" : "workGet", body); return cached;
    }
    public void send(String op, JsonObject body) {
        if (busy) return;
        var packet = body.deepCopy(); packet.addProperty("action", "community");
        packet.addProperty("section", "home"); packet.addProperty("op", op);
        operation = op; busy = true; retry = false;
        redrawAfterReply = !reading() || !listLoaded || !id.isEmpty() && !data.has("task");
        notice = reading() ? "Загрузка…" : "Сохраняем изменения…";
        dispatch(session.begin(packet, !reading(), clock.getAsLong()));
    }
    private void dispatch(JsonObject packet) { request = Json.str(packet, "request"); sender.accept(packet); }
    public void retry() { if (busy || !retry) return; busy = true; retry = false; redrawAfterReply = true; dispatch(session.retry(clock.getAsLong())); }
    public boolean tick() {
        boolean changed = undo != null && clock.getAsLong() >= undoUntil;
        if (changed) undo = null;
        if (session.timeout(clock.getAsLong())) { busy = false; retry = true; notice = "Нет ответа. Повтор безопасен."; changed = true; }
        return changed;
    }
    public boolean undoAvailable() { return undo != null && clock.getAsLong() < undoUntil; }
    public void undoStatus() {
        if (busy || !undoAvailable()) return;
        var inverse = undo; undo = null; undoing = true; send("workStatus", inverse);
    }
    public Outcome receive(JsonObject reply, boolean selectFirst) {
        if (!session.receive(reply)) return Outcome.IGNORED;
        busy = false; boolean redraw = redrawAfterReply; redrawAfterReply = false;
        if (reply.has("error")) {
            var failure = MenuData.failure(reply); notice = failure.message();
            retry = !Set.of("INVALID", "FORBIDDEN", "NOT_FOUND", "CONFLICT", "EXPIRED").contains(failure.code());
            return new Outcome(true, true, false, failure.code().equals("CONFLICT"), false);
        }
        notice = ""; boolean changed = false;
        if (reply.has("deleted")) {
            for (int n = tasks.size() - 1; n >= 0; n--) if (Json.str(tasks.get(n).getAsJsonObject(), "id").equals(id)) tasks.remove(n);
            clearSelection(); undo = null; return new Outcome(true, true, false, false, false);
        }
        if (reply.has("tasks")) {
            boolean create = reply.has("canCreate") && reply.get("canCreate").getAsBoolean();
            changed = !listLoaded || !tasks.equals(reply.get("tasks")) || !nextCursor.equals(Json.opt(reply, "nextCursor", "")) || canCreate != create;
            tasks = reply.getAsJsonArray("tasks").deepCopy(); nextCursor = Json.opt(reply, "nextCursor", "");
            listLoaded = true; canCreate = create;
            if (id.isEmpty() && selectFirst && !tasks.isEmpty()) { id = Json.str(tasks.get(0).getAsJsonObject(), "id"); changed = true; }
            if (!id.isEmpty()) return new Outcome(true, changed || redraw, false, false, true);
        }
        if (reply.has("task")) {
            var task = reply.getAsJsonObject("task"); var typed = MenuData.Task.read(task);
            if (operation.equals("workStatus") && !undoing && data.has("task")) {
                undo = new JsonObject(); undo.addProperty("group", group); undo.addProperty("task", typed.id());
                undo.addProperty("revision", typed.revision()); undo.addProperty("status", MenuData.Task.read(data.getAsJsonObject("task")).status());
                undoUntil = clock.getAsLong() + 10000; notice = "Статус изменён";
            } else if (!reading()) { notice = undoing ? "Изменение отменено" : "Изменения сохранены"; undoing = false; }
            id = typed.id(); changed = !data.has("task") || !UiPayload.same(data.getAsJsonObject("task"), task);
            data.add("task", task.deepCopy());
            if (undo != null && undo.get("revision").getAsLong() != typed.revision()) undo = null;
            boolean found = false;
            for (int n = 0; n < tasks.size(); n++) if (Json.str(tasks.get(n).getAsJsonObject(), "id").equals(id)) { tasks.set(n, task.deepCopy()); found = true; break; }
            if (!found) tasks.add(task.deepCopy());
        }
        return new Outcome(true, changed || redraw || !reading(), operation.equals("workSave"), false, false);
    }
}
