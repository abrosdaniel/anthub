package dev.abros.anthub.core;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Canonical wire versions are also embedded in release metadata by the publisher. */
public final class WireProtocols {
    private static final JsonObject CURRENT=load();
    private WireProtocols() {}
    private static JsonObject load() {
        try(var input=WireProtocols.class.getResourceAsStream("/anthub/protocols.json")) {
            return Json.parse(new String(java.util.Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8));
        } catch(Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    public static int version(String name) { return CURRENT.get(name).getAsInt(); }
    public static JsonObject current() { return CURRENT.deepCopy(); }
    public static boolean compatible(JsonObject protocols) {
        for(String name:List.of("pack","auth","menu","helper")) {
            if(!protocols.has(name)||!CURRENT.get(name).equals(protocols.get(name)))return false;
        }
        return true;
    }
}
