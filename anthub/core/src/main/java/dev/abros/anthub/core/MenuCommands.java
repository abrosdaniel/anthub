package dev.abros.anthub.core;

import com.google.gson.JsonObject;
import java.util.*;

/** Shared command semantics. Object ownership and live permissions are always checked by the server. */
public final class MenuCommands {
    public enum Effect { READ, WRITE, SIGNAL }
    public record Definition(String action, String operation, Effect effect, String administrativePermission) {}
    private static final Set<String> COMMUNITY_READS = Set.of("hud", "list", "detail", "workList", "workGet", "workMembers", "workArchivePreview",
        "globalSearch", "toolsPrivacy", "toolsPlaces", "toolsFollowing", "toolsMapSettings", "toolsMapPeers",
        "plusProfile", "plusIgnores", "plusItemRead");
    private static final Set<String> READS = Set.of("players", "reports", "myReports", "myReport", "history", "menuData",
        "playerAdministration", "adminDashboard", "rolePreview", "diagnostics", "exportCommunity");
    private static final Set<String> WRITES = Set.of("report", "reply", "moderate", "reportManage", "announce", "scheduledAnnouncements",
        "pinAnnouncement", "maintenance", "restart", "reloadMenu");
    private static final Map<String, String> PERMISSIONS = Map.of(
        "scheduledAnnouncements", "anthub.announce", "announce", "anthub.announce", "pinAnnouncement", "anthub.announce", "maintenance", "anthub.maintenance",
        "restart", "anthub.restart", "reports", "anthub.reports", "reply", "anthub.reports",
        "reportManage", "anthub.reports", "diagnostics", "anthub.diagnostics");
    private static final Set<String> ACTIONS = Set.of("state", "report", "reports", "diagnostics", "announce", "scheduledAnnouncements", "maintenance",
        "restart", "menuData", "myReports", "reply", "players", "moderate", "history", "reloadMenu", "community", "myReport",
        "subscribe", "pinAnnouncement", "exportCommunity", "playerAdministration", "moderationVote", "adminDashboard", "reportManage", "serverConfig", "rolePreview");
    public static boolean known(String action) { return ACTIONS.contains(action); }
    public static String administrativePermission(String action) { return PERMISSIONS.getOrDefault(action, "anthub.admin"); }
    public static Definition describe(JsonObject packet) {
        String action = Json.opt(packet, "action", ""), op = Json.opt(packet, "op", "");
        if (op.isEmpty()) op = Json.opt(packet, "operation", "");
        Effect effect;
        if (action.equals("community")) effect = COMMUNITY_READS.contains(op) ? Effect.READ : Effect.WRITE;
        else if (action.equals("moderationVote")) effect = op.equals("view") ? Effect.READ : Effect.WRITE;
        else if (action.equals("scheduledAnnouncements")) effect = Set.of("list","preview").contains(op)?Effect.READ:Effect.WRITE;
        else if (action.equals("serverConfig")) effect = op.equals("apply") ? Effect.WRITE : Effect.READ;
        else if (READS.contains(action) || action.equals("reportManage") && Set.of("bulkPreview", "staff").contains(op)) effect = Effect.READ;
        else effect = WRITES.contains(action) ? Effect.WRITE : Effect.SIGNAL;
        return new Definition(action, op, effect, administrativePermission(action));
    }
    /** Validate envelope shape centrally; domain handlers validate field values and ownership. */
    public static void validate(JsonObject packet) {
        for (String field : List.of("action", "op", "operation", "request", "operationId")) if (packet.has(field)) {
            var value = packet.get(field);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().length() > 128)
                throw new IllegalArgumentException("Invalid command field: " + field);
        }
        if (Json.opt(packet, "action", "").isBlank()) throw new IllegalArgumentException("Command action is required");
    }
    public static JsonObject prepare(JsonObject packet, long now) {
        validate(packet); var copy = packet.deepCopy();
        if (describe(copy).effect() == Effect.WRITE && !copy.has("operationId")) {
            copy.addProperty("operationId", UUID.randomUUID().toString()); copy.addProperty("issuedAt", now);
        }
        return copy;
    }
    private MenuCommands() {}
}
