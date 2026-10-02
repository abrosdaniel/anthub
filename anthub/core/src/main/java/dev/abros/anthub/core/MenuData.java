package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.*;
/** Typed read boundary. Extra server fields remain available without forcing a protocol change. */
public final class MenuData {
 public record Failure(String code,String message,Map<String,String> fields){public Failure{fields=Map.copyOf(fields);}}
 public record Page(List<JsonObject> entries,String nextCursor,boolean more){public Page{entries=entries.stream().map(JsonObject::deepCopy).toList();}public List<JsonObject> entries(){return entries.stream().map(JsonObject::deepCopy).toList();}}
 public record Notice(String id,String title,String section,String target,long at,boolean read,int count){public static Notice read(JsonObject j){return new Notice(Json.opt(j,"id",""),Json.opt(j,"title","Уведомление"),Json.opt(j,"section","notifications"),Json.opt(j,"target",""),number(j,"at"),flag(j,"read"),Math.max(1,(int)number(j,"groupCount")));}}
 public record Task(String id,String title,String description,String status,long revision,boolean manage){public static Task read(JsonObject j){return new Task(Json.opt(j,"id",""),Json.opt(j,"title",""),Json.opt(j,"description",""),Json.opt(j,"status","open"),number(j,"revision"),flag(j,"manage"));}}
 public static Failure failure(JsonObject j){var fields=new LinkedHashMap<String,String>();if(j.has("fields")&&j.get("fields").isJsonObject())j.getAsJsonObject("fields").entrySet().forEach(e->{if(e.getValue().isJsonPrimitive())fields.put(e.getKey(),e.getValue().getAsString());});return new Failure(Json.opt(j,"code","UNAVAILABLE"),Json.opt(j,"text","Не удалось получить данные"),fields);}
 public static Page page(JsonObject j,String key){var entries=new ArrayList<JsonObject>();if(j.has(key)&&j.get(key).isJsonArray())for(var e:j.getAsJsonArray(key)){if(!e.isJsonObject())throw new IllegalArgumentException("Expected object in "+key);entries.add(e.getAsJsonObject());}return new Page(entries,Json.opt(j,"nextCursor",""),flag(j,"more"));}
 private static long number(JsonObject j,String key){return j.has(key)&&!j.get(key).isJsonNull()?j.get(key).getAsLong():0;}
 private static boolean flag(JsonObject j,String key){return j.has(key)&&!j.get(key).isJsonNull()&&j.get(key).getAsBoolean();}
 private MenuData(){}
}
