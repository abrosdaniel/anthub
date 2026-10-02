package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.*;
/** Explicit fields only: never infer a diff from an unrestricted account/configuration object. */
public final class ChangeSummary {
 private ChangeSummary(){}
 public static JsonArray between(JsonObject before,JsonObject after,Map<String,String> fields){var rows=new JsonArray();for(var field:fields.entrySet()){var a=before.get(field.getKey());var b=after.get(field.getKey());if(Objects.equals(a,b))continue;var row=new JsonObject();row.addProperty("field",field.getKey());row.addProperty("label",field.getValue());row.add("before",a==null?new JsonPrimitive("Не задано"):a.deepCopy());row.add("after",b==null?new JsonPrimitive("Не задано"):b.deepCopy());rows.add(row);}return rows;}
 public static String text(JsonElement value){if(value==null||value.isJsonNull())return "Не задано";return value.isJsonPrimitive()?value.getAsString():value.toString();}
}
