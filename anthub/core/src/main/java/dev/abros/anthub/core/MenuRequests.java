package dev.abros.anthub.core;
import com.google.gson.JsonObject;
/** Compatibility facade for callers; command semantics have one source of truth. */
public final class MenuRequests {
 public static boolean read(JsonObject request){return MenuCommands.describe(request).effect()==MenuCommands.Effect.READ;}
 public static boolean mutation(JsonObject request){return MenuCommands.describe(request).effect()==MenuCommands.Effect.WRITE;}
 public static boolean sameRead(JsonObject first,JsonObject second){return read(first)&&read(second)&&UiPayload.same(first,second);}
 private MenuRequests(){}
}
