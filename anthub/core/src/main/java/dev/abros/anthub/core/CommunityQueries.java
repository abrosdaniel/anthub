package dev.abros.anthub.core;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Read models with keyset continuations, visibility checks and explicit archive filtering. */
final class CommunityQueries {
 private final PgDatabase db;
 CommunityQueries(PgDatabase db){this.db=db;}
 private String scope(CommunityStore.Actor actor,JsonObject input,String section){return Hashes.sha256((actor.id()+"|"+actor.admin()+"|"+section+"|"+Json.opt(input,"query","")+"|"+Json.opt(input,"member","")+"|"+Boolean.toString(input.has("mine")&&input.get("mine").getAsBoolean())+"|"+Boolean.toString(input.has("participating")&&input.get("participating").getAsBoolean())+"|"+Boolean.toString(input.has("archive")&&input.get("archive").getAsBoolean())+"|"+Boolean.toString(input.has("trash")&&input.get("trash").getAsBoolean())).getBytes(StandardCharsets.UTF_8));}
 private long ceiling(String table)throws Exception{try(var q=db.connection().createStatement();var row=q.executeQuery("SELECT coalesce(max("+(table.equals("notices")?"id":"sequence")+"),0) FROM "+table)){row.next();return row.getLong(1);}}
 JsonArray list(CommunityStore.Actor actor,JsonObject input,String section,long now,JsonObject config)throws Exception {
  String scope=scope(actor,input,section),token=Json.opt(input,"cursor","");CommunityCursor after=token.isEmpty()?null:CommunityCursor.decode(token,scope);long cap=after==null?ceiling("documents"):after.ceiling();
  var sources=new ArrayList<String>();for(String source:section.equals("home")?List.of("events"):List.of(section))if(config.getAsJsonArray("sections").contains(new JsonPrimitive(source)))sources.add(source);
  var args=new ArrayList<Object>();args.add(actor.admin());args.add(actor.id());args.add(input.has("mine")&&input.get("mine").getAsBoolean());args.add(actor.id());args.add(Json.opt(input,"member",""));args.add(Json.opt(input,"member",""));args.add(Json.opt(input,"query",""));args.add(cap);
  String sql="SELECT body,sequence FROM documents WHERE section=ANY(?) AND (body->>'status'<>'hidden' OR ? OR body->>'owner'=?) AND (NOT ? OR body->>'owner'=?) AND (?='' OR section<>'groups' OR jsonb_exists(body->'members',?)) AND strpos(lower(body->>'title'),lower(?))>0 AND sequence<=?";
  String archived="(body->>'status' IN ('closed','done','declined','cancelled','hidden') OR section IN ('board','polls') AND (body->>'endsAt')::bigint<=? OR section='events' AND (body->>'startsAt')::bigint<=?)";
  boolean trash=input.has("trash")&&input.get("trash").getAsBoolean();
  sql+=" AND "+(trash?"body->>'status'='deleted' AND (? OR body->>'owner'=?)":"body->>'status'<>'deleted'");if(trash){args.add(actor.admin());args.add(actor.id());}
  boolean archive=input.has("archive")&&input.get("archive").getAsBoolean();if(!trash){sql+=" AND "+(archive?"":"NOT ")+archived;args.add(now);args.add(now);}
  if(input.has("participating")&&input.get("participating").getAsBoolean()){
   sql+=" AND (section='board' AND jsonb_exists(body->'responses',?) OR section='groups' AND (jsonb_exists(body->'members',?) OR jsonb_exists(body->'applications',?) OR jsonb_exists(body->'invitations',?)) OR section='events' AND jsonb_exists(body->'participants',?) OR section='polls' AND jsonb_exists(body->'votes',?) OR section='ideas' AND jsonb_exists(body->'supporters',?))";
   for(int n=0;n<7;n++)args.add(actor.id());
  }
  if(section.equals("home")){
   sql+=" AND (body->>'owner'=? OR jsonb_exists(body->'participants',?))";args.add(actor.id());args.add(actor.id());
  }
  boolean events=section.equals("events")||section.equals("home");String sortTime=section.equals("home")?"coalesce((body->>'startsAt')::bigint,0)":"(body->>'startsAt')::bigint";if(after!=null){if(events){sql+=" AND ("+sortTime+">? OR "+sortTime+"=? AND sequence<?)";args.add(after.time());args.add(after.time());}else sql+=" AND sequence<?";args.add(after.sequence());}
  sql+=events?" ORDER BY "+sortTime+" ASC,sequence DESC LIMIT 11":" ORDER BY sequence DESC LIMIT 11";
  var out=new JsonArray();long last=0,time=0;boolean more=false;
  try(var q=db.connection().prepareStatement(sql)){q.setArray(1,db.connection().createArrayOf("text",sources.toArray()));for(int i=0;i<args.size();i++)q.setObject(i+2,args.get(i));try(var rows=q.executeQuery()){while(rows.next()){if(out.size()==(section.equals("home")?3:10)){more=!section.equals("home");break;}var document=Json.parse(rows.getString(1));var preview=CommunityPreview.of(document);if(section.equals("home"))preview.addProperty("attention",switch(Json.str(document,"section")){case "groups"->document.getAsJsonObject("invitations").has(actor.id())?"Вас пригласили":"Заявки ждут ответа";case "board"->Json.str(document,"owner").equals(actor.id())?"Новые отклики":"Есть ответ на ваш отклик";case "polls"->"Вы ещё не голосовали";default->"Ближайшее событие";});preview.addProperty("isOwner",Json.str(document,"owner").equals(actor.id()));preview.addProperty("isParticipant",document.has("participants")&&document.getAsJsonObject("participants").has(actor.id()));preview.addProperty("supported",document.has("supporters")&&document.getAsJsonObject("supporters").has(actor.id()));out.add(preview);last=rows.getLong(2);time=events&&document.has("startsAt")?document.get("startsAt").getAsLong():0;}}}
  if(more)out.get(out.size()-1).getAsJsonObject().addProperty("_nextCursor",new CommunityCursor(last,time,cap,scope).encode());return out;
 }
 JsonArray notices(CommunityStore.Actor actor,JsonObject input)throws Exception {
  String scope=scope(actor,input,"notifications"),token=Json.opt(input,"cursor","");CommunityCursor after=token.isEmpty()?null:CommunityCursor.decode(token,scope);long cap=after==null?ceiling("notices"):after.ceiling();var out=new JsonArray();long last=0;boolean more=false;
  try(var q=db.connection().prepareStatement("SELECT id,body,read FROM notices WHERE recipient=? AND id<=? AND id<? ORDER BY id DESC LIMIT 11")){q.setString(1,actor.id());q.setLong(2,cap);q.setLong(3,after==null?Long.MAX_VALUE:after.sequence());try(var rows=q.executeQuery()){while(rows.next()){if(out.size()==10){more=true;break;}last=rows.getLong(1);var j=Json.parse(rows.getString(2));j.addProperty("id",Long.toString(last));j.addProperty("read",rows.getBoolean(3));out.add(j);}}}
  if(more)out.get(out.size()-1).getAsJsonObject().addProperty("_nextCursor",new CommunityCursor(last,0,cap,scope).encode());return out;
 }
}
