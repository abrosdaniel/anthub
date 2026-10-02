package dev.abros.anthub.core;

import com.google.gson.*;
import java.util.function.*;

/** Request lifecycle for list features. Views supply filters and render defensive snapshots. */
public final class PagedMenuController {
    public record Outcome(boolean accepted, boolean changed, boolean failed) {}
    private final String kind;
    private final Consumer<JsonObject> sender;
    private final LongSupplier clock;
    private final RequestSession session = new RequestSession();
    private final PagedWindow window = new PagedWindow();
    private JsonObject reply = new JsonObject();
    private JsonArray entries = new JsonArray();
    private String request = "", error = "";
    private int page;
    private boolean busy, failed;
    private long sent, dirtyAt;
    public PagedMenuController(String kind, Consumer<JsonObject> sender, LongSupplier clock) {
        this.kind = kind; this.sender = sender; this.clock = clock;
    }
    public boolean busy() { return busy; }
    public boolean failed() { return failed; }
    public String error() { return error; }
    public int page() { return page; }
    public boolean more() { return window.more(); }
    public int loadedPage() { return window.loadedPage(); }
    public long sent() { return sent; }
    public JsonArray entries() { return entries.deepCopy(); }
    public JsonObject reply() { return reply.deepCopy(); }
    public void invalidate() { if (dirtyAt == 0) dirtyAt = clock.getAsLong(); }
    public boolean refreshDue() { return !busy && !failed && window.refreshDue(clock.getAsLong()); }
    public boolean dirtyDue() { return !busy && !failed && dirtyAt > 0 && clock.getAsLong() - sent > 750; }
    public boolean nextAllowed() { return !busy && !failed && more() && !window.refreshing() && clock.getAsLong() - sent > 600; }
    public void reset() { if (busy) return; window.reset(); entries = new JsonArray(); page = 0; dirtyAt = 0; }
    public void refresh() { if (busy) return; window.refresh(); dirtyAt = 0; page = 0; }
    public void leave() { if (busy) { session.cancel(); busy = false; invalidate(); } }
    public boolean request(JsonObject command, int requestedPage) {
        if (busy) return false;
        page = Math.max(0, Math.min(kind.equals("history") ? 100 : 1000, requestedPage));
        var packet = command.deepCopy(); packet.addProperty("page", page); packet.addProperty("cursor", window.cursor(page));
        sent = clock.getAsLong(); busy = true; failed = false; error = "";
        dispatch(session.begin(packet, MenuRequests.mutation(packet), sent)); return true;
    }
    private void dispatch(JsonObject packet) { request = Json.str(packet, "request"); sender.accept(packet); }
    public void retry() { if (busy || !failed) return; busy = true; failed = false; sent = clock.getAsLong(); dispatch(session.retry(sent)); }
    public void failure(String text) { session.cancel(); busy = false; failed = true; error = text; window.stopRefresh(); }
    public boolean timeout() { if (!session.timeout(clock.getAsLong())) return false; busy = false; failed = true; error = "Нет ответа. Нажмите «Повторить»."; window.stopRefresh(); return true; }
    public Outcome receive(JsonObject response) {
        if (!request.equals(Json.opt(response, "request", "")) || response.has("page") && response.get("page").getAsInt() != page)
            return new Outcome(false, false, false);
        if (!session.receive(response)) return new Outcome(false, false, false);
        busy = false;
        if (response.has("error")) { failed = true; error = MenuData.failure(response).message(); window.stopRefresh(); return new Outcome(true, true, true); }
        var source = response.has("menu") ? response.getAsJsonObject("menu") : response;
        String key = response.has("menu") ? kind : kind.equals("players") ? "players" : kind.equals("history") || kind.equals("notifications") ? "entries" : "reports";
        var decoded = MenuData.page(source, key);
        boolean cursorPaging = response.has("nextCursor");
        int pageSize = kind.equals("players") ? 20 : kind.equals("history") ? 10 : 5;
        boolean hasMore = cursorPaging ? !Json.opt(response, "nextCursor", "").isEmpty() : decoded.entries().size() == pageSize;
        window.accept(page, new MenuData.Page(decoded.entries(), Json.opt(response, "nextCursor", ""), hasMore), hasMore, clock.getAsLong());
        var next = window.entries(); if (kind.equals("notifications")) next = NotificationGroups.arrange(next);
        boolean changed = !entries.equals(next) || !metadata(reply).equals(metadata(response));
        entries = next; reply = response.deepCopy(); failed = false; error = "";
        return new Outcome(true, changed, false);
    }
    private static JsonObject metadata(JsonObject response) {
        var metadata = new JsonObject(); for (String key : java.util.List.of("actions", "preferences"))
            if (response.has(key)) metadata.add(key, response.get(key).deepCopy()); return metadata;
    }
}
