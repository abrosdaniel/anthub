package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.Set;
/** Explicit public allowlist: no responses, applications, voters, or moderation reasons. */
public final class EntryHistory {
 private EntryHistory(){}
 public static void append(JsonObject document,String operation,String author,long now){
  if(!Set.of("create","edit","close","complete","status","reschedule","cancel","recruiting","delete","restore").contains(operation))return;
  var entries=document.has("history")?document.getAsJsonArray("history"):new JsonArray();var item=new JsonObject();item.addProperty("operation",operation);item.addProperty("author",author);item.addProperty("at",now);
  for(String key: switch(operation){case "reschedule"->new String[]{"startsAt"};case "status"->new String[]{"status","answer"};case "close","complete","cancel"->new String[]{"status"};case "recruiting"->new String[]{"recruiting"};default->new String[]{};})if(document.has(key))item.add(key,document.get(key).deepCopy());
  entries.add(item);while(entries.size()>50)entries.remove(0);document.add("history",entries);
 }
}
