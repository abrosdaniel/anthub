package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.*;
/** Connection-scoped bounded cache. Correlation IDs and mutations never become cache keys. */
public final class MenuReadCache {
 private record Entry(JsonObject query,JsonObject value,long at){}
 private final Map<String,Entry> values=new LinkedHashMap<>(32,.75f,true);
 private String key(JsonObject q){var j=q.deepCopy();for(String k:List.of("request","operationId","issuedAt","revision"))j.remove(k);return Json.GSON.toJson(j);}
 public synchronized JsonObject get(JsonObject q,long now){var e=values.get(key(q));if(e==null)return null;if(now-e.at>30000){values.remove(key(q));return null;}return e.value.deepCopy();}
 public synchronized void put(JsonObject q,JsonObject reply,long now){if(!MenuRequests.read(q)||reply.has("error")||Json.GSON.toJson(reply).length()>131072)return;values.put(key(q),new Entry(q.deepCopy(),reply.deepCopy(),now));while(values.size()>32)values.remove(values.keySet().iterator().next());}
 public synchronized void invalidate(String section,String id){values.entrySet().removeIf(e->{if(section.isEmpty())return true;var query=e.getValue().query;var reply=e.getValue().value;String topic=Json.opt(query,"section",Json.opt(reply,"section",""));if(!topic.equals(section)){return (section.equals("home")||section.equals("groups"))&&(reply.has("task")||reply.has("tasks"))||section.equals("players")&&Json.opt(reply,"kind","").equals("players");}String target=Json.opt(query,"id",Json.opt(query,"task",""));return id.isEmpty()||target.isEmpty()||target.equals(id);});}
 public synchronized void clear(){values.clear();}public synchronized int size(){return values.size();}
}
