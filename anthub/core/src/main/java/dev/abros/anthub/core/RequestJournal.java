package dev.abros.anthub.core;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Receipts and mutations commit together. Expired commands can never be executed again. */
public final class RequestJournal {
 private final PgDatabase db;
 public RequestJournal(PgDatabase db){this.db=db;}
 public JsonObject execute(String actor,JsonObject input,PgDatabase.Work<JsonObject> work)throws Exception {
  if(!input.has("operationId"))return work.run(); // Internal commands need no network receipt.
  String id=Json.str(input,"operationId");UUID.fromString(id);
  long now=System.currentTimeMillis(),issued=input.get("issuedAt").getAsLong();
  if(issued>now+60000||issued<now-86400000L)throw new CommunityFailure(CommunityFailure.Code.EXPIRED,"Срок отправки истёк. Обновите запись и повторите действие.");
  var payload=input.deepCopy();payload.remove("request");String digest=Hashes.sha256(canonical(payload).getBytes(StandardCharsets.UTF_8));
  db.lock("receipt:"+actor+":"+id);
  try(var q=db.connection().prepareStatement("SELECT digest,response FROM request_receipts WHERE actor=? AND id=?")){
   q.setString(1,actor);q.setString(2,id);try(var row=q.executeQuery()){if(row.next()){
    if(!digest.equals(row.getString(1)))throw new CommunityFailure(CommunityFailure.Code.INVALID,"Повторный запрос отличается от отправленного. Обновите запись.");
    var response=Json.parse(row.getString(2));response.addProperty("request",Json.opt(input,"request",""));response.addProperty("replayed",true);return response;
   }}
  }
  var result=work.run();
  try(var q=db.connection().prepareStatement("INSERT INTO request_receipts(actor,id,digest,response,created) VALUES(?,?,?,?::jsonb,?)")){q.setString(1,actor);q.setString(2,id);q.setString(3,digest);q.setString(4,Json.GSON.toJson(result));q.setLong(5,now);q.executeUpdate();}
  try(var q=db.connection().prepareStatement("DELETE FROM request_receipts WHERE created<?")){q.setLong(1,now-172800000L);q.executeUpdate();}
  return result;
 }
 private static String canonical(JsonElement value){if(value.isJsonObject()){var ordered=new TreeMap<String,String>();value.getAsJsonObject().entrySet().forEach(e->ordered.put(e.getKey(),canonical(e.getValue())));var join=new StringJoiner(",","{","}");ordered.forEach((k,v)->join.add(Json.GSON.toJson(k)+":"+v));return join.toString();}if(value.isJsonArray()){var join=new StringJoiner(",","[","]");value.getAsJsonArray().forEach(e->join.add(canonical(e)));return join.toString();}return value.toString();}
}
