package dev.abros.anthub.core;
import com.google.gson.*;
final class EventConflicts {
 static JsonArray read(PgDatabase db,CommunityStore store,CommunityStore.Actor actor,JsonObject event)throws Exception {
  long start=event.get("startsAt").getAsLong(),end=start+(event.has("durationMinutes")?event.get("durationMinutes").getAsLong():60)*60000;var rows=new JsonArray();try(var q=db.connection().prepareStatement("SELECT body FROM community_documents WHERE section='events' AND id<>? AND body->>'status'='open' AND (body->>'owner'=? OR jsonb_exists(body->'participants',?)) AND (body->>'startsAt')::bigint<? AND (body->>'startsAt')::bigint+coalesce((body->>'durationMinutes')::bigint,60)*60000>? ORDER BY (body->>'startsAt')::bigint LIMIT 10")){q.setString(1,Json.str(event,"id"));q.setString(2,actor.id());q.setString(3,actor.id());q.setLong(4,end);q.setLong(5,start);try(var rs=q.executeQuery()){while(rs.next()){var other=Json.parse(rs.getString(1));if(!new CommunityPlus(db,store).visible(actor,other))continue;var row=new JsonObject();for(String key:java.util.List.of("id","title","startsAt"))row.add(key,other.get(key));rows.add(row);}}}return rows;
 }
 private EventConflicts(){}
}
