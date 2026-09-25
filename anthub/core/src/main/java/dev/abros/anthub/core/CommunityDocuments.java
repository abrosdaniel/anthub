package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.*;
/** Child collections are independent rows; unchanged members and votes are never rewritten. */
final class CommunityDocuments {
 static final List<String> FIELDS=List.of("members","applications","invitations","participants","responses","votes","supporters","reminded");
 static void put(PgDatabase db,JsonObject value)throws Exception{
  String id=Json.str(value,"id");var body=value.deepCopy();FIELDS.forEach(body::remove);
  if(body.toString().length()>200000)throw new IllegalArgumentException("Запись достигла предела размера");
  try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,?,?::jsonb) ON CONFLICT(id) DO UPDATE SET body=excluded.body")){q.setString(1,id);q.setString(2,Json.str(value,"section"));q.setString(3,body.toString());q.executeUpdate();}
  for(String kind:FIELDS){var rows=value.has(kind)?value.getAsJsonObject(kind):new JsonObject();
   try(var q=db.connection().prepareStatement("DELETE FROM community_relations WHERE document=? AND kind=? AND NOT(actor=ANY(?))")){q.setString(1,id);q.setString(2,kind);q.setArray(3,db.connection().createArrayOf("text",rows.keySet().toArray()));q.executeUpdate();}
   if(!rows.isEmpty())try(var q=db.connection().prepareStatement("INSERT INTO community_relations SELECT ?,?,entry.key,entry.value FROM jsonb_each(?::jsonb) entry ON CONFLICT(document,kind,actor) DO UPDATE SET value=excluded.value WHERE community_relations.value IS DISTINCT FROM excluded.value")){q.setString(1,id);q.setString(2,kind);q.setString(3,rows.toString());q.executeUpdate();}

  }
 }
}
