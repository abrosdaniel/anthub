package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.*;
/** Explicit interchangeable items. Disjoint sets prevent counting one stock in two requirements. */
public final class ResourceRequirements {
 private ResourceRequirements() {}
 public static List<String> items(JsonObject row){var result=new ArrayList<String>();result.add(Json.str(row,"item"));if(row.has("alternatives"))for(var e:row.getAsJsonArray("alternatives"))result.add(e.getAsString());return List.copyOf(result);}
 public static void validate(JsonArray rows){if(rows==null||rows.size()>30)throw new IllegalArgumentException("До 30 ресурсов");var used=new HashSet<String>();for(var e:rows){var row=e.getAsJsonObject();Json.keys(row,"item","amount","alternatives");int amount=row.get("amount").getAsBigDecimal().intValueExact();if(amount<1||amount>1000000)throw new IllegalArgumentException("Количество: 1–1000000");var ids=items(row);if(ids.size()>17)throw new IllegalArgumentException("До 16 замен ресурса");for(String id:ids)if(!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!used.add(id))throw new IllegalArgumentException("Предмет или замена указаны несколько раз: "+id);}}
 public static List<JsonObject> ordered(JsonArray rows,JsonObject stock){var result=new ArrayList<JsonObject>();for(var e:rows)result.add(e.getAsJsonObject());result.sort(Comparator.comparingLong((JsonObject r)->missing(r,stock)).reversed());return List.copyOf(result);}
 public static long count(JsonObject row,JsonObject stock){long count=0;for(String id:items(row))if(stock.has(id))count=Math.addExact(count,stock.get(id).getAsLong());return count;}
 public static long missing(JsonObject row,JsonObject stock){return Math.max(0,row.get("amount").getAsLong()-count(row,stock));}
 public static JsonObject reserve(JsonArray rows,JsonObject stock,JsonObject used){validate(rows);var result=new JsonObject();for(var e:rows){var row=e.getAsJsonObject();long remaining=row.get("amount").getAsLong();for(String id:items(row)){long free=Math.max(0,(stock.has(id)?stock.get(id).getAsLong():0)-(used.has(id)?used.get(id).getAsLong():0));long allocated=Math.min(remaining,free);if(allocated>0)result.addProperty(id,allocated);remaining-=allocated;}if(remaining>0)throw new IllegalArgumentException("Не хватает "+remaining+" для "+Json.str(row,"item"));}return result;}
}
