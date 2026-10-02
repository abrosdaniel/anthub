package dev.abros.anthub.core;

import com.google.gson.*;
import java.util.*;
import java.util.function.*;

/** Community application controller: revisioned commands, live refresh and paged detail collections. */
public final class CommunityWorkspace {
    public record Filters(String query, String member, String sort, boolean trash, boolean archive, boolean mine, boolean participating) {}
    public record Outcome(boolean accepted, boolean changed, boolean failed, boolean conflict) {}
    private final String section, id;
    private final Consumer<JsonObject> sender;
    private final LongSupplier clock;
    private final RequestSession session = new RequestSession();
    private final PagedWindow list = new PagedWindow();
    private final NavigableMap<Integer, JsonObject> details = new TreeMap<>();
    private final Map<Integer, String> cursors = new HashMap<>();
    private JsonObject data = new JsonObject();
    private String request = "", operation = "", status = "";
    private boolean busy, uncertain, failed, more;
    private int page, loadedPage, refreshThrough;
    private long sent, dirtyAt, nextAt;
    public CommunityWorkspace(String section, String id, Consumer<JsonObject> sender, LongSupplier clock) {
        this.section = section; this.id = id; this.sender = sender; this.clock = clock;
    }
    public boolean busy() { return busy; }
    public boolean uncertain() { return uncertain; }
    public boolean failed() { return failed; }
    public boolean more() { return more; }
    public String operation() { return operation; }
    public String status() { return status; }
    public long sent() { return sent; }
    public JsonObject data() { return data.deepCopy(); }
    public boolean matches(JsonObject reply) { return request.equals(Json.opt(reply, "request", "")); }
    public void invalidate() { if (dirtyAt == 0) dirtyAt = clock.getAsLong(); }
    public boolean dirtyDue() { return !busy && !uncertain && dirtyAt > 0 && clock.getAsLong() - sent > 750; }
    public boolean reading() { return Set.of("list", "detail").contains(operation); }
    public boolean leave() { if (busy && !reading()) return false; if (busy) { session.cancel(); busy = false; invalidate(); } return true; }
    public void close() { session.cancel(); busy = false; }
    public void reset() { if (busy) return; page = loadedPage = refreshThrough = 0; list.reset(); details.clear(); cursors.clear(); nextAt = 0; }
    public void load(Filters filters) { if (busy || uncertain) return; refreshThrough = loadedPage; list.refresh(); dirtyAt = 0; page = 0; send(id.isEmpty() ? "list" : "detail", new JsonObject(), filters); }
    public void next(Filters filters) { if (busy || uncertain || nextAt > 0 || !more || clock.getAsLong() - sent < 600) return; page = loadedPage + 1; send(id.isEmpty() ? "list" : "detail", new JsonObject(), filters); }
    /** Form owns its correlation/retry session; this controller supplies the feature context. */
    public void submitForm(String op, JsonObject values) {
        var packet = values.deepCopy(); packet.addProperty("action", "community"); packet.addProperty("section", section);
        packet.addProperty("id", id); packet.addProperty("op", op); sender.accept(MenuCommands.prepare(packet, clock.getAsLong()));
    }
    public void readNotice(String noticeId) {
        var packet = new JsonObject(); packet.addProperty("action", "community"); packet.addProperty("section", "notifications");
        packet.addProperty("op", "read"); packet.addProperty("id", noticeId); sender.accept(MenuCommands.prepare(packet, clock.getAsLong()));
    }
    public void send(String op, JsonObject body, Filters filters) {
        if (busy || uncertain) return;
        operation = op; if (!reading()) { refreshThrough = loadedPage; page = 0; }
        var packet = body.deepCopy(); if (data.has("detail") && data.getAsJsonObject("detail").has("revision")) packet.add("revision", data.getAsJsonObject("detail").get("revision").deepCopy());
        packet.addProperty("action", "community"); packet.addProperty("section", section); packet.addProperty("op", op);
        packet.addProperty("id", id); packet.addProperty("page", page); packet.addProperty("cursor", page == 0 ? "" : cursors.getOrDefault(page, ""));
        packet.addProperty("query", filters.query()); packet.addProperty("member", filters.member()); packet.addProperty("sort", filters.sort());
        packet.addProperty("trash", filters.trash()); packet.addProperty("archive", filters.archive()); packet.addProperty("mine", filters.mine()); packet.addProperty("participating", filters.participating());
        busy = true; failed = false; sent = clock.getAsLong(); status = reading() ? "Загрузка…" : "Сохраняем изменения…";
        dispatch(session.begin(packet, !reading(), sent));
    }
    private void dispatch(JsonObject packet) { request = Json.str(packet, "request"); sender.accept(packet); }
    public void retry() { if (!uncertain || busy) return; busy = true; failed = false; sent = clock.getAsLong(); dispatch(session.retry(sent)); }
    public boolean tick(Filters filters) {
        if (session.timeout(clock.getAsLong())) { busy = false; uncertain = true; nextAt = 0; status = "Нет ответа. Нажмите «Повторить»."; return true; }
        if (!busy && !uncertain && nextAt > 0 && clock.getAsLong() >= nextAt) { nextAt = 0; page++; send(id.isEmpty() ? "list" : "detail", new JsonObject(), filters); }
        else if (dirtyDue()) load(filters);
        return false;
    }
    public Outcome receive(JsonObject response) {
        if (!session.receive(response)) return new Outcome(false, false, false, false);
        boolean wasUncertain = uncertain; busy = false; uncertain = false;
        if (response.has("error")) {
            var error = MenuData.failure(response); status = error.message(); failed = true; nextAt = 0;
            boolean conflict = error.code().equals("CONFLICT") && reading();
            if (conflict) { cursors.clear(); invalidate(); status = "Запись обновилась. Загружаем актуальные данные…"; }
            return new Outcome(true, true, true, conflict);
        }
        var merged = response.deepCopy();
        if (id.isEmpty() && merged.has("entries")) {
            var decoded = MenuData.page(merged, "entries"); list.accept(page, decoded, !decoded.nextCursor().isEmpty(), clock.getAsLong());
            merged.add("entries", list.entries()); loadedPage = list.loadedPage(); more = list.more();
            if (!more) refreshThrough = Math.min(refreshThrough, loadedPage);
            cursors.put(page + 1, decoded.nextCursor());
        } else if (merged.has("detail")) {
            String next = Json.opt(merged, "nextCursor", ""); cursors.put(page + 1, next);
            merged.add("detail", mergeDetail(merged.getAsJsonObject("detail"), !next.isEmpty()));
        }
        nextAt = page < refreshThrough ? clock.getAsLong() + 600 : 0;
        if (!id.isEmpty() && merged.has("names") && data.has("names")) {
            var names = data.getAsJsonObject("names").deepCopy(); merged.getAsJsonObject("names").entrySet().forEach(e -> names.add(e.getKey(), e.getValue().deepCopy())); merged.add("names", names);
        }
        boolean changed = wasUncertain || !reading() || !UiPayload.same(data, merged);
        data = merged; status = ""; failed = false; return new Outcome(true, changed, false, false);
    }
    private JsonObject mergeDetail(JsonObject detail, boolean remaining) {
        details.put(page, detail.deepCopy()); loadedPage = Math.max(loadedPage, page);
        if (!remaining) { details.tailMap(page, false).clear(); cursors.keySet().removeIf(index -> index > page + 1); loadedPage = page; refreshThrough = Math.min(refreshThrough, page); }
        if (page == loadedPage) more = remaining;
        var result = detail.deepCopy();
        if (detail.has("groupItems")) { var batches = new ArrayList<JsonArray>(); for (var batch : details.values()) if (batch.has("groupItems")) batches.add(batch.getAsJsonArray("groupItems")); result.add("groupItems", MenuPages.merge(batches)); }
        for (String key : List.of("responses", "members", "applications", "participants", "invitations")) if (detail.has(key + "Count")) {
            var values = new JsonObject(); for (var batch : details.values()) if (batch.has(key)) batch.getAsJsonObject(key).entrySet().forEach(e -> values.add(e.getKey(), e.getValue().deepCopy())); result.add(key, values);
        }
        return result;
    }
}
