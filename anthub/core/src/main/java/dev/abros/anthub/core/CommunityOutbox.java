package dev.abros.anthub.core;
import com.google.gson.*;
/** Transactional invalidations; duplicate deliveries are harmless. No content in event payloads. */
final class CommunityOutbox {
 static void add(PgDatabase db,String topic,String entity,String recipient)throws Exception{try(var q=db.connection().prepareStatement("INSERT INTO community_events(topic,entity,recipient,created) VALUES(?,?,?,?)")){q.setString(1,topic);q.setString(2,entity);q.setString(3,recipient);q.setLong(4,System.currentTimeMillis());q.executeUpdate();}}
 static JsonArray pending(PgDatabase db)throws Exception{var out=new JsonArray();try(var q=db.connection().prepareStatement("SELECT id,topic,entity,recipient FROM community_events WHERE NOT delivered ORDER BY id LIMIT 256");var rs=q.executeQuery()){while(rs.next()){var j=new JsonObject();j.addProperty("sequence",rs.getLong(1));j.addProperty("topic",rs.getString(2));j.addProperty("entity",rs.getString(3));j.addProperty("recipient",rs.getString(4));out.add(j);}}return out;}
 static void acknowledge(PgDatabase db,JsonArray events)throws Exception{try(var q=db.connection().prepareStatement("UPDATE community_events SET delivered=TRUE WHERE id=?")){for(var event:events){q.setLong(1,event.getAsJsonObject().get("sequence").getAsLong());q.addBatch();}q.executeBatch();}try(var q=db.connection().prepareStatement("DELETE FROM community_events WHERE delivered AND created<?")){q.setLong(1,System.currentTimeMillis()-86400000L);q.executeUpdate();}}
}
