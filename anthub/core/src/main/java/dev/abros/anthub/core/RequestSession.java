package dev.abros.anthub.core;
import com.google.gson.JsonObject;
import java.util.UUID;
/** One in-flight request. Retry preserves command identity, but rotates reply correlation. */
public final class RequestSession {
 private JsonObject command;private String correlation="";private long sent;private boolean pending;
 public JsonObject begin(JsonObject payload,boolean mutation,long now){
  if(pending)throw new IllegalStateException("Request already pending");
  command=payload.deepCopy();command.remove("request");
  if(mutation&&!command.has("operationId")){command.addProperty("operationId",UUID.randomUUID().toString());command.addProperty("issuedAt",now);}
  return retry(now);
 }
 public JsonObject retry(long now){if(pending||command==null)throw new IllegalStateException("Request cannot be retried");pending=true;sent=now;correlation=UUID.randomUUID().toString();var out=command.deepCopy();out.addProperty("request",correlation);return out;}
 public boolean receive(JsonObject response){if(!pending||!correlation.equals(Json.opt(response,"request","")))return false;pending=false;return true;}
 public boolean timeout(long now){if(pending&&now-sent>=15000){pending=false;return true;}return false;}
 public void cancel(){pending=false;correlation="";}
 public boolean pending(){return pending;}
 public JsonObject command(){return command==null?null:command.deepCopy();}
}
