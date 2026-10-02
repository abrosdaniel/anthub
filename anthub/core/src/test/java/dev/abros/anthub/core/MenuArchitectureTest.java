package dev.abros.anthub.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class MenuArchitectureTest {
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static JsonObject read(String correlation) { return json("{\"action\":\"community\",\"section\":\"board\",\"op\":\"list\",\"request\":\"" + correlation + "\"}"); }
    private static JsonObject response(JsonObject packet, String fields) { var reply = json("{" + fields + "}"); reply.addProperty("request", Json.str(packet, "request")); return reply; }
    private static MenuData.Page page(String entries, String cursor) { return MenuData.page(json("{\"entries\":" + entries + ",\"nextCursor\":\"" + cursor + "\"}"), "entries"); }
    @Test void transportCoalescesOnlyTheSameOwnersReads() {
        var queue = new MenuTransport<Object>(); var owner = new Object(); var other = new Object();
        queue.enqueue(read("old"), owner, false, 0); queue.enqueue(read("new"), owner, false, 0); queue.enqueue(read("other"), other, false, 0);
        assertEquals(2, queue.queued()); assertEquals(2, queue.trackedReads());
        var packets = queue.dispatch(0, owner); assertEquals(1, packets.size()); assertEquals("new", Json.str(packets.getFirst(), "request")); assertEquals(1, queue.trackedReads());
    }
    @Test void slowerOldReadCannotOverwriteNewerCacheSnapshot() {
        var queue = new MenuTransport<Object>(); var owner = new Object(); var old = read("old"); var latest = read("latest");
        queue.enqueue(old, owner, false, 0); queue.dispatch(0, owner); queue.enqueue(latest, owner, false, 1); queue.dispatch(550, owner);
        queue.receive(response(latest, "\"title\":\"new\""), 551); queue.receive(response(old, "\"title\":\"old\""), 552);
        assertEquals("new", Json.str(queue.cached(latest, 553), "title"));
    }
    @Test void cancelledResponsesCannotRepopulateCache() {
        var queue = new MenuTransport<Object>(); var owner = new Object(); var packet = read("one");
        queue.enqueue(packet, owner, false, 0); queue.dispatch(0, owner); queue.cancel(owner);
        queue.receive(response(packet, "\"entries\":[]"), 1); assertNull(queue.cached(packet, 2));
    }
    @Test void transportPreservesWritesAcrossNavigationAndCopiesPackets() {
        var queue = new MenuTransport<Object>(); var owner = new Object(); var command = json("{\"action\":\"community\",\"op\":\"create\",\"title\":\"original\"}");
        queue.enqueue(command, owner, false, 0); command.addProperty("title", "changed"); queue.cancel(owner);
        var write = queue.dispatch(0, new Object()).getFirst(); assertEquals("original", Json.str(write, "title")); assertTrue(write.has("operationId")); assertFalse(command.has("operationId"));
    }
    @Test void initialBackgroundStateSurvivesScreenNavigation() {
        var queue = new MenuTransport<Object>(); var loginScreen = new Object();
        var state = json("{\"action\":\"state\",\"request\":\"initial\"}");
        assertTrue(queue.enqueue(state, null, true, 0));
        queue.cancel(loginScreen);
        var packets = queue.dispatch(0, new Object());
        assertEquals(1, packets.size()); assertEquals("state", Json.str(packets.getFirst(), "action"));
    }
    @Test void backgroundWritesAndQueueOverflowAreRejected() {
        var queue = new MenuTransport<Object>(); assertThrows(IllegalArgumentException.class, () -> queue.enqueue(json("{\"action\":\"community\",\"op\":\"create\"}"), null, true, 0));
        for (int n = 0; n < 64; n++) { var q = read("r" + n); q.addProperty("id", "id" + n); assertTrue(queue.enqueue(q, null, false, 0)); }
        assertFalse(queue.enqueue(read("overflow"), null, false, 0)); assertEquals(64, queue.queued());
    }
    @Test void transportThrottlesAndExpiresReadsWithoutDroppingWrites() {
        var queue = new MenuTransport<Object>(); var owner = new Object(); queue.enqueue(read("first"), owner, false, 0);
        assertEquals(1, queue.dispatch(0, owner).size()); queue.enqueue(read("second"), owner, false, 1);
        assertTrue(queue.dispatch(549, owner).isEmpty()); assertEquals(1, queue.dispatch(550, owner).size());
        queue.enqueue(read("expired"), owner, false, 0); queue.enqueue(json("{\"action\":\"community\",\"op\":\"create\"}"), owner, false, 0);
        var packets = queue.dispatch(15000, owner); assertEquals(1, packets.size()); assertEquals("create", Json.str(packets.getFirst(), "op")); assertEquals(0, queue.trackedReads());
    }
    @Test void mutationAndInvalidationPreventLateReadCaching() {
        var queue = new MenuTransport<Object>(); var q = read("old"); queue.enqueue(q, null, false, 0); queue.dispatch(0, null);
        queue.enqueue(json("{\"action\":\"community\",\"op\":\"create\"}"), null, false, 1); queue.receive(response(q, "\"entries\":[]"), 2); assertNull(queue.cached(q, 3));
        queue.enqueue(read("new"), null, false, 3); queue.invalidate("board", ""); queue.receive(response(read("new"), "\"entries\":[]"), 4); assertNull(queue.cached(q, 5));
    }
    @Test void refreshTrimsStalePagesAndDeduplicatesMovingRows() {
        var window = new PagedWindow(); window.accept(0, page("[{\"id\":\"a\"},{\"id\":\"b\"}]", "next"), true, 0); window.accept(1, page("[{\"id\":\"b\"},{\"id\":\"c\"}]", ""), false, 0);
        assertEquals(3, window.entries().size()); window.refresh(); window.accept(0, page("[{\"id\":\"a\"}]", ""), false, 1);
        assertEquals(1, window.entries().size()); assertEquals(0, window.loadedPage()); assertFalse(window.more()); assertFalse(window.refreshDue(601)); assertEquals("", window.cursor(2));
    }
    @Test void refreshKeepsVisibleRowsUntilLaterPagesArrive() {
        var window = new PagedWindow(); window.accept(0, page("[{\"id\":\"a\"}]", "next"), true, 0); window.accept(1, page("[{\"id\":\"b\"}]", ""), false, 0); window.refresh();
        window.accept(0, page("[{\"id\":\"c\"}]", "next2"), true, 1); assertEquals(2, window.entries().size()); assertFalse(window.refreshDue(600)); assertTrue(window.refreshDue(601));
    }
    @Test void listControllerRejectsLateAndWrongPageResponses() {
        var now = new AtomicLong(100); var sent = new ArrayList<JsonObject>(); var model = new PagedMenuController("players", sent::add, now::get); model.request(json("{\"action\":\"players\"}"), 0);
        var old = sent.getLast(); model.leave(); model.request(json("{\"action\":\"players\",\"query\":\"new\"}"), 0);
        assertFalse(model.receive(response(old, "\"players\":[]")).accepted()); assertFalse(model.receive(response(sent.getLast(), "\"page\":1,\"players\":[]")).accepted()); assertTrue(model.busy());
        assertTrue(model.receive(response(sent.getLast(), "\"page\":0,\"players\":[],\"nextCursor\":\"\"")).accepted()); assertFalse(model.busy());
    }
    @Test void inboxRetryRetainsMutationIdentityAndReturnsReadPreferences() {
        var now = new AtomicLong(1); var sent = new ArrayList<JsonObject>(); var model = new PagedMenuController("notifications", sent::add, now::get);
        model.request(json("{\"action\":\"community\",\"section\":\"notifications\",\"op\":\"read\"}"), 0); String operation = Json.str(sent.getLast(), "operationId"), old = Json.str(sent.getLast(), "request");
        now.set(15001); assertTrue(model.timeout()); model.retry(); assertEquals(operation, Json.str(sent.getLast(), "operationId")); assertNotEquals(old, Json.str(sent.getLast(), "request"));
        model.receive(response(sent.getLast(), "\"entries\":[],\"nextCursor\":\"\",\"preferences\":{\"sound\":true}")); assertTrue(model.reply().has("preferences")); assertFalse(model.failed());
    }
    @Test void listRefreshOfUnchangedDataDoesNotRequestRecomposition() {
        var sent = new ArrayList<JsonObject>(); var model = new PagedMenuController("players", sent::add, () -> 1); var request = json("{\"action\":\"players\"}");
        model.request(request, 0); assertTrue(model.receive(response(sent.getLast(), "\"players\":[{\"uuid\":\"a\"}],\"nextCursor\":\"\"")).changed());
        model.refresh(); model.request(request, 0); assertFalse(model.receive(response(sent.getLast(), "\"players\":[{\"uuid\":\"a\"}],\"nextCursor\":\"\"")).changed());
    }
    private static TaskWorkspace taskModel(List<JsonObject> sent, AtomicLong now) { return new TaskWorkspace("", "t", sent::add, q -> null, now::get); }
    private static JsonObject taskReply(JsonObject packet, String status, int revision) { return response(packet, "\"task\":{\"id\":\"t\",\"status\":\"" + status + "\",\"revision\":" + revision + "}"); }
    @Test void taskUndoUsesLatestRevisionAndExpires() {
        var sent = new ArrayList<JsonObject>(); var now = new AtomicLong(1); var model = taskModel(sent, now); model.load(); model.receive(taskReply(sent.getLast(), "open", 1), false);
        var command = model.body(); command.addProperty("status", "working"); model.send("workStatus", command); model.receive(taskReply(sent.getLast(), "working", 2), false); assertTrue(model.undoAvailable());
        model.undoStatus(); assertEquals(2, sent.getLast().get("revision").getAsInt()); assertEquals("open", Json.str(sent.getLast(), "status")); model.receive(taskReply(sent.getLast(), "open", 3), false); assertFalse(model.undoAvailable()); assertEquals("Изменение отменено", model.notice());
        model.send("workStatus", command); model.receive(taskReply(sent.getLast(), "working", 4), false); now.set(10001); assertTrue(model.tick()); assertFalse(model.undoAvailable());
    }
    @Test void taskConflictDoesNotRetryAnInvalidRevision() {
        var sent = new ArrayList<JsonObject>(); var now = new AtomicLong(1); var model = taskModel(sent, now); model.send("workStatus", model.body());
        var result = model.receive(response(sent.getLast(), "\"error\":true,\"code\":\"CONFLICT\",\"text\":\"changed\""), false);
        assertTrue(result.conflict()); assertFalse(model.retryAvailable()); assertFalse(model.busy());
    }
    @Test void taskRetryIsIdempotentAndOldReplyCannotChangeSelection() {
        var sent = new ArrayList<JsonObject>(); var now = new AtomicLong(1); var model = taskModel(sent, now); model.send("workStatus", model.body()); var old = sent.getLast(); now.set(15001); assertTrue(model.tick()); model.retry();
        assertEquals(Json.str(old, "operationId"), Json.str(sent.getLast(), "operationId")); assertFalse(model.receive(taskReply(old, "done", 2), false).accepted());
        model.receive(taskReply(sent.getLast(), "done", 2), false); model.load(); old = sent.getLast(); assertTrue(model.select("other")); model.load(); assertFalse(model.receive(taskReply(old, "open", 1), false).accepted()); assertEquals("other", model.id());
    }
    @Test void taskSnapshotsCannotMutateControllerState() {
        var sent = new ArrayList<JsonObject>(); var model = taskModel(sent, new AtomicLong(1)); model.load(); var reply = taskReply(sent.getLast(), "open", 1); model.receive(reply, false);
        reply.getAsJsonObject("task").addProperty("status", "tampered"); model.data().getAsJsonObject("task").addProperty("status", "tampered"); model.tasks().remove(0); assertEquals("open", Json.str(model.data().getAsJsonObject("task"), "status")); assertEquals(1, model.tasks().size());
    }
    @Test void commandCatalogueDistinguishesReadPreviewFromWritesAndValidatesEnvelope() {
        assertEquals(MenuCommands.Effect.READ, MenuCommands.describe(json("{\"action\":\"reportManage\",\"operation\":\"staff\"}")).effect());
        assertEquals(MenuCommands.Effect.READ, MenuCommands.describe(json("{\"action\":\"serverConfig\",\"op\":\"preview\"}")).effect());
        assertEquals(MenuCommands.Effect.WRITE, MenuCommands.describe(json("{\"action\":\"serverConfig\",\"op\":\"apply\"}")).effect());
        assertEquals("anthub.reports", MenuCommands.administrativePermission("reportManage")); assertEquals("anthub.admin", MenuCommands.administrativePermission("rolePreview"));
        assertThrows(IllegalArgumentException.class, () -> MenuCommands.validate(json("{\"action\":{}}")));
        assertThrows(IllegalArgumentException.class, () -> MenuCommands.validate(new JsonObject()));
    }
    private static CommunityWorkspace.Filters filters() { return new CommunityWorkspace.Filters("", "", "default", false, false, false, false); }
    @Test void communityDetailPagesMergeMembersAndRefreshRemovesStaleMembers() {
        var sent = new ArrayList<JsonObject>(); var now = new AtomicLong(1); var model = new CommunityWorkspace("groups", "g", sent::add, now::get); model.load(filters());
        model.receive(response(sent.getLast(), "\"detail\":{\"revision\":1,\"membersCount\":2,\"members\":{\"a\":\"member\"}},\"nextCursor\":\"next\"")); now.set(602); model.next(filters());
        assertEquals("next", Json.str(sent.getLast(), "cursor")); model.receive(response(sent.getLast(), "\"detail\":{\"revision\":1,\"membersCount\":2,\"members\":{\"b\":\"member\"}},\"nextCursor\":\"\"")); assertEquals(2, model.data().getAsJsonObject("detail").getAsJsonObject("members").size());
        model.load(filters()); model.receive(response(sent.getLast(), "\"detail\":{\"revision\":2,\"membersCount\":1,\"members\":{\"c\":\"member\"}},\"nextCursor\":\"\"")); assertEquals(1, model.data().getAsJsonObject("detail").getAsJsonObject("members").size());
    }
    @Test void communityRetryAndRevisionAreOwnedByController() {
        var sent = new ArrayList<JsonObject>(); var now = new AtomicLong(1); var model = new CommunityWorkspace("board", "b", sent::add, now::get); model.load(filters()); model.receive(response(sent.getLast(), "\"detail\":{\"revision\":7},\"nextCursor\":\"\""));
        var body = new JsonObject(); model.send("close", body, filters()); assertFalse(body.has("revision")); assertEquals(7, sent.getLast().get("revision").getAsInt()); assertFalse(model.leave()); var original = sent.getLast(); now.set(15001); assertTrue(model.tick(filters())); model.retry(); assertEquals(Json.str(original, "operationId"), Json.str(sent.getLast(), "operationId")); assertFalse(model.receive(response(original, "\"detail\":{\"revision\":8}")).accepted());
    }
}
