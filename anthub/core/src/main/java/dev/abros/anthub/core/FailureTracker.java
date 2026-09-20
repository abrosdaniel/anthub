package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.*;
/** Bounded diagnostics contain only classified operation names and exception class names. */
public final class FailureTracker {
    public record Incident(String id,String operation,String type,long at,int count,boolean log){}
    private final LinkedHashMap<String,Incident> recent=new LinkedHashMap<>();
    public synchronized Incident record(String operation,Throwable failure,long now){
        String safe=operation.matches("[a-zA-Z.:_-]{1,80}")?operation:"request",type=failure.getClass().getSimpleName();
        String key=safe+":"+type;var old=recent.remove(key);boolean log=old==null||now-old.at()>=60000;
        var event=new Incident(log?UUID.randomUUID().toString():old.id(),safe,type,log?now:old.at(),old==null?1:old.count()+1,log);
        recent.put(key,event);while(recent.size()>64)recent.remove(recent.keySet().iterator().next());return event;
    }
    public synchronized JsonArray snapshot(){var result=new JsonArray();var entries=new ArrayList<>(recent.values());Collections.reverse(entries);for(var incident:entries.subList(0,Math.min(10,entries.size()))){var row=new JsonObject();row.addProperty("id",incident.id());row.addProperty("operation",incident.operation());row.addProperty("type",incident.type());row.addProperty("at",incident.at());row.addProperty("count",incident.count());result.add(row);}return result;}
    public synchronized void clear(){recent.clear();}
}
