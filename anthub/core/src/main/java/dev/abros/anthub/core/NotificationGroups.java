package dev.abros.anthub.core;
import com.google.gson.*;import java.util.*;
public final class NotificationGroups {
 public static JsonArray arrange(JsonArray entries){var groups=new LinkedHashMap<String,JsonObject>();for(var e:entries){var row=e.getAsJsonObject();String target=Json.opt(row,"target","");String key=Json.opt(row,"section","")+"|"+(target.isEmpty()?"notice:"+Json.opt(row,"id",""):target);var first=groups.get(key);if(first==null){groups.put(key,row.deepCopy());continue;}first.addProperty("groupCount",first.has("groupCount")?first.get("groupCount").getAsInt()+1:2);if(row.has("read")&&!row.get("read").getAsBoolean())first.addProperty("read",false);}var out=new JsonArray();groups.values().forEach(out::add);return out;}
 private NotificationGroups(){}
}
