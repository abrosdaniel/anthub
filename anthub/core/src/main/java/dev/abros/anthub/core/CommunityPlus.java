package dev.abros.anthub.core;

import com.google.gson.*;
import java.time.*;
import java.util.*;

/** Server-authorized personal and group tools. Never trusts client visibility or role flags. */
final class CommunityPlus {
 private final PgDatabase db;private final CommunityStore store;
 CommunityPlus(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 private static void require(boolean ok){if(!ok)throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Действие недоступно");}
 private static String text(JsonObject j,String key,int limit){String value=Json.opt(j,key,"").strip();if(value.length()>limit||value.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw new IllegalArgumentException("Некорректное поле: "+key);return value;}
 private boolean member(String group,String player)throws Exception{if(group.isEmpty())return false;try(var q=db.connection().prepareStatement("SELECT 1 FROM community_relations r JOIN documents d ON d.id=r.document WHERE r.document=? AND r.kind='members' AND r.actor=? AND d.body->>'status'='open'")){q.setString(1,group);q.setString(2,player);try(var rows=q.executeQuery()){return rows.next();}}}
 boolean visible(CommunityStore.Actor a,JsonObject j)throws Exception{
  if(!Json.str(j,"section").equals("events")||a.admin()||Json.str(j,"owner").equals(a.id()))return true;
  return switch(Json.opt(j,"visibility","public")){case "public"->true;case "group"->member(Json.opt(j,"group",""),a.id());case "invited"->j.has("eventInvites")&&j.getAsJsonArray("eventInvites").contains(new JsonPrimitive(a.id()));default->false;};
 }
 void requireVisible(CommunityStore.Actor a,JsonObject j)throws Exception{if(!visible(a,j))throw new CommunityFailure(CommunityFailure.Code.NOT_FOUND,"Запись недоступна");}
 void allowContact(String recipient,String sender)throws Exception{try(var q=db.connection().prepareStatement("SELECT 1 FROM community_ignores WHERE player=? AND target=?")){q.setString(1,recipient);q.setString(2,sender);try(var rows=q.executeQuery()){if(rows.next())throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Игрок ограничил обращения");}}}
 void prepare(CommunityStore.Actor a,JsonObject in,JsonObject j)throws Exception{
  String section=Json.str(j,"section");
  if(section.equals("events")){
   String visibility=Json.opt(in,"visibility","public");require(Set.of("public","group","invited").contains(visibility));if(visibility.equals("group"))require(member(Json.opt(j,"group",""),a.id()));j.addProperty("visibility",visibility);
   var invites=new JsonArray();String names=text(in,"invitees",1200);for(String name:names.split(",")){if(name.isBlank())continue;String target=person(name.strip());allowContact(target,a.id());if(!invites.contains(new JsonPrimitive(target)))invites.add(target);}
   if(invites.size()>30)throw new IllegalArgumentException("Не более 30 приглашённых");j.add("eventInvites",invites);
  }
  if(section.equals("board")&&!Json.opt(in,"trade","none").equals("none")){
   String trade=Json.str(in,"trade");require(Set.of("buy","sell","exchange").contains(trade));String item=text(in,"item",100),terms=text(in,"terms",500);int quantity=in.has("quantity")?in.get("quantity").getAsBigDecimal().intValueExact():1;require(!item.isBlank()&&!terms.isBlank()&&quantity>0&&quantity<=1000000);var offer=new JsonObject();offer.addProperty("type",trade);offer.addProperty("item",item);offer.addProperty("quantity",quantity);offer.addProperty("terms",terms);j.add("trade",offer);
  }
 }
 private String person(String name)throws Exception{try(var q=db.connection().prepareStatement("SELECT id FROM people WHERE id=? OR lower(name)=lower(?)")){q.setString(1,name);q.setString(2,name);try(var rows=q.executeQuery()){if(!rows.next())throw new IllegalArgumentException("Игрок не найден: "+name);String id=rows.getString(1);if(rows.next())throw new IllegalArgumentException("Неоднозначный ник. Укажите UUID");return id;}}}
 void repeat(CommunityStore.Actor a,JsonObject in,JsonObject j)throws Exception{
  if(!Json.str(j,"section").equals("events"))return;for(var target:j.getAsJsonArray("eventInvites"))store.note(target.getAsString(),"events",Json.str(j,"id"),"Приглашение на событие: "+Json.str(j,"title"));String repeat=Json.opt(in,"repeat","none");if(repeat.equals("none"))return;require(Set.of("weekly","fortnightly","monthly").contains(repeat));int count=in.has("occurrences")?in.get("occurrences").getAsBigDecimal().intValueExact():8;require(count>=2&&count<=26);
  try(var q=db.connection().prepareStatement("SELECT count(*) FROM documents WHERE section='events'")){try(var rows=q.executeQuery()){rows.next();require(rows.getInt(1)+count-1<=2000);}}
  ZoneId zone=ZoneId.of(Json.opt(in,"timezone","UTC"));var start=Instant.ofEpochMilli(j.get("startsAt").getAsLong()).atZone(zone);String series=Json.str(j,"id");j.addProperty("series",series);j.addProperty("repeat",repeat);store.put(j);
  for(int n=1;n<count;n++){var next=j.deepCopy();next.addProperty("id",UUID.randomUUID().toString());next.addProperty("startsAt",(repeat.equals("monthly")?start.plusMonths(n):start.plusWeeks((repeat.equals("weekly")?1:2)*n)).toInstant().toEpochMilli());next.addProperty("revision",0);store.put(next);}
 }
 JsonObject request(CommunityStore.Actor a,JsonObject in,String section)throws Exception{
  String op=Json.str(in,"op"),id=Json.opt(in,"id","");var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section",section);out.addProperty("request",Json.opt(in,"request",""));
  if(op.equals("plusProfile")||op.equals("plusProfileSave")){
   String target=id.isEmpty()?a.id():id;UUID.fromString(target);
   if(op.endsWith("Save")){require(target.equals(a.id()));try(var q=db.connection().prepareStatement("INSERT INTO community_profile VALUES(?,?,?) ON CONFLICT(player) DO UPDATE SET about=excluded.about,interests=excluded.interests")){q.setString(1,target);q.setString(2,text(in,"about",300));q.setString(3,text(in,"interests",100));q.executeUpdate();}CommunityOutbox.add(db,"players",target,"");}
   var profile=new JsonObject();profile.addProperty("about","");profile.addProperty("interests","");try(var q=db.connection().prepareStatement("SELECT about,interests FROM community_profile WHERE player=?")){q.setString(1,target);try(var rows=q.executeQuery()){if(rows.next()){profile.addProperty("about",rows.getString(1));profile.addProperty("interests",rows.getString(2));}}}out.add("profile",profile);return out;
  }
  if(op.equals("plusIgnore")||op.equals("plusIgnores")){
   if(op.equals("plusIgnore")){String target=person(text(in,"target",100));require(!target.equals(a.id()));boolean remove=in.has("remove")&&in.get("remove").getAsBoolean();db.lock("community:ignore:"+a.id());if(!remove)try(var limit=db.connection().prepareStatement("SELECT count(*) FROM community_ignores WHERE player=?")){limit.setString(1,a.id());try(var count=limit.executeQuery()){count.next();require(count.getInt(1)<100);}}try(var q=db.connection().prepareStatement(remove?"DELETE FROM community_ignores WHERE player=? AND target=?":"INSERT INTO community_ignores VALUES(?,?) ON CONFLICT DO NOTHING")){q.setString(1,a.id());q.setString(2,target);q.executeUpdate();}}
   var entries=new JsonArray();try(var q=db.connection().prepareStatement("SELECT i.target,p.name FROM community_ignores i LEFT JOIN people p ON p.id=i.target WHERE player=? ORDER BY p.name")){q.setString(1,a.id());try(var rows=q.executeQuery()){while(rows.next()){var row=new JsonObject();row.addProperty("id",rows.getString(1));row.addProperty("name",Objects.toString(rows.getString(2),rows.getString(1)));entries.add(row);}}}out.add("entries",entries);return out;
  }
  var j=store.get(id,!op.equals("plusItemRead"));require(Json.str(j,"section").equals(section));requireVisible(a,j);require(Json.str(j,"status").equals("open"));boolean manager=store.manager(j,a),owner=Json.str(j,"owner").equals(a.id());
  if(op.equals("plusItemRead")){require(section.equals("groups")&&(member(id,a.id())||a.admin()));try(var q=db.connection().prepareStatement("SELECT body,revision FROM community_group_items WHERE id=? AND group_id=?")){q.setString(1,Json.str(in,"itemId"));q.setString(2,id);try(var rows=q.executeQuery()){require(rows.next());var item=Json.parse(rows.getString(1));item.addProperty("revision",rows.getLong(2));out.add("item",item);return out;}}}
  if(op.equals("plusReminder")){
   require(section.equals("events"));int minutes=in.get("minutes").getAsInt();require(Set.of(-1,0,5,15,60).contains(minutes));try(var limit=db.connection().prepareStatement("SELECT count(*) FROM community_event_preferences WHERE event=? AND player<>?")){limit.setString(1,id);limit.setString(2,a.id());try(var rows=limit.executeQuery()){rows.next();require(rows.getInt(1)<300);}}try(var q=db.connection().prepareStatement("INSERT INTO community_event_preferences VALUES(?,?,?) ON CONFLICT(event,player) DO UPDATE SET minutes=excluded.minutes")){q.setString(1,id);q.setString(2,a.id());q.setInt(3,minutes);q.executeUpdate();}CommunityOutbox.add(db,"home",id,a.id());
  }else if(op.equals("plusCancelSeries")){
   require(section.equals("events")&&(owner||a.admin())&&j.has("series"));try(var q=db.connection().prepareStatement("SELECT id FROM documents WHERE body->>'series'=? AND body->>'status'='open' AND (body->>'startsAt')::bigint>? ORDER BY id FOR UPDATE")){q.setString(1,Json.str(j,"series"));q.setLong(2,System.currentTimeMillis());try(var rows=q.executeQuery()){while(rows.next()){var event=store.get(rows.getString(1));event.addProperty("status","cancelled");store.put(event);for(String target:event.getAsJsonObject("participants").keySet())store.note(target,"events",Json.str(event,"id"),"Серия событий отменена: "+Json.str(event,"title"));}}}j=store.get(id);
  }else if(op.equals("plusRemoveMember")){
   require(section.equals("groups")&&manager);String target=text(in,"target",100);require(!target.equals(Json.str(j,"owner"))&&j.getAsJsonObject("members").has(target));require(owner||a.admin()||j.has("assistantRemoval")&&j.get("assistantRemoval").getAsBoolean()&&j.getAsJsonObject("members").get(target).getAsString().equals("member"));String reason=text(in,"reason",300);require(!reason.isBlank());j.getAsJsonObject("members").remove(target);store.put(j);store.note(target,"groups",id,"Вас исключили из "+Json.str(j,"title")+": "+reason);
  }else if(op.equals("plusAssistantRemoval")){require(section.equals("groups")&&(owner||a.admin()));j.addProperty("assistantRemoval",in.get("enabled").getAsBoolean());store.put(j);
  }else if(Set.of("plusItemSave","plusItemDelete","plusTaskStatus").contains(op)){
   require(section.equals("groups")&&(member(id,a.id())||a.admin()));String item=Json.opt(in,"itemId","");JsonObject previous=null;long revision=0;
   if(!item.isEmpty())try(var q=db.connection().prepareStatement("SELECT body,revision FROM community_group_items WHERE id=? AND group_id=? FOR UPDATE")){q.setString(1,item);q.setString(2,id);try(var rows=q.executeQuery()){require(rows.next());previous=Json.parse(rows.getString(1));revision=rows.getLong(2);}}
   if(previous!=null&&(!in.has("itemRevision")||in.get("itemRevision").getAsLong()!=revision))throw new CommunityFailure(CommunityFailure.Code.CONFLICT,"Запись изменена. Откройте её заново");
   if(op.equals("plusTaskStatus")){require(previous!=null&&Json.str(previous,"kind").equals("task")&&(manager||a.id().equals(Json.opt(previous,"assignee",""))));String state=text(in,"status",20);require(Set.of("open","working","done").contains(state));previous.addProperty("status",state);saveItem(item,id,previous,revision+1);
   }else{require(manager);if(op.equals("plusItemDelete")){require(previous!=null);try(var q=db.connection().prepareStatement("DELETE FROM community_group_items WHERE id=? AND group_id=?")){q.setString(1,item);q.setString(2,id);q.executeUpdate();}}
    else{String kind=text(in,"kind",20);require(Set.of("task","place").contains(kind));if(previous!=null)require(kind.equals(Json.str(previous,"kind")));var body=new JsonObject();String title=text(in,"title",100);require(!title.isBlank());body.addProperty("title",title);body.addProperty("kind",kind);body.addProperty("description",text(in,"description",1000));
     if(kind.equals("task")){String assignee=text(in,"assignee",100);if(!assignee.isEmpty()){assignee=person(assignee);require(member(id,assignee));}body.addProperty("assignee",assignee);String due=text(in,"dueAt",40);body.addProperty("dueAt",due.isEmpty()?0:Instant.parse(due).toEpochMilli());body.addProperty("status",previous==null?"open":Json.str(previous,"status"));}
     else{require(in.has("location")&&!in.get("location").isJsonNull());body.add("location",CommunityLocation.read(in.getAsJsonObject("location")).json());}
     if(item.isEmpty()){try(var q=db.connection().prepareStatement("SELECT count(*) FROM community_group_items WHERE group_id=?")){q.setString(1,id);try(var rows=q.executeQuery()){rows.next();require(rows.getInt(1)<200);}}item=UUID.randomUUID().toString();}saveItem(item,id,body,revision+1);
    }
   }store.put(j);CommunityOutbox.add(db,"home",id,"");
  }else throw new IllegalArgumentException("Неизвестное действие");
  var read=in.deepCopy();read.addProperty("op","detail");read.remove("cursor");return store.execute(a,read,"detail",section);
 }
 private void saveItem(String id,String group,JsonObject body,long revision)throws Exception{try(var q=db.connection().prepareStatement("INSERT INTO community_group_items VALUES(?,?,?,?::jsonb,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body,revision=excluded.revision")){q.setString(1,id);q.setString(2,group);q.setString(3,Json.str(body,"kind"));q.setString(4,body.toString());q.setLong(5,revision);q.executeUpdate();}}
 void enrich(CommunityStore.Actor a,JsonObject source,JsonObject detail,int page)throws Exception{
  String section=Json.str(source,"section"),id=Json.str(source,"id");
  if(section.equals("events")){detail.remove("eventInvites");int minutes=Json.str(source,"owner").equals(a.id())||source.getAsJsonObject("participants").has(a.id())?5:-1;try(var q=db.connection().prepareStatement("SELECT minutes FROM community_event_preferences WHERE event=? AND player=?")){q.setString(1,id);q.setString(2,a.id());try(var rows=q.executeQuery()){if(rows.next())minutes=rows.getInt(1);}}detail.addProperty("reminderMinutes",minutes);}
  if(section.equals("groups")){
   boolean member=member(id,a.id())||a.admin();String visibility="group_id=? AND (? OR kind='place' AND NOT coalesce((body->'location'->>'membersOnly')::boolean,false))";var items=new JsonArray();
   try(var q=db.connection().prepareStatement("SELECT count(*) FROM community_group_items WHERE "+visibility)){q.setString(1,id);q.setBoolean(2,member);try(var rows=q.executeQuery()){rows.next();detail.addProperty("groupItemsCount",rows.getInt(1));}}
   try(var q=db.connection().prepareStatement("SELECT id,body,revision FROM community_group_items WHERE "+visibility+" ORDER BY kind,id LIMIT 5 OFFSET ?")){q.setString(1,id);q.setBoolean(2,member);q.setInt(3,page*5);try(var rows=q.executeQuery()){while(rows.next()){var body=Json.parse(rows.getString(2));body.addProperty("id",rows.getString(1));body.addProperty("revision",rows.getLong(3));items.add(body);}}}
   detail.add("groupItems",items);detail.addProperty("canRemoveMember",Json.str(source,"owner").equals(a.id())||a.admin()||store.manager(source,a)&&source.has("assistantRemoval")&&source.get("assistantRemoval").getAsBoolean());
  }

 }
 void profiles(JsonArray entries)throws Exception{
  var ids=new ArrayList<String>();for(var e:entries)ids.add(Json.str(e.getAsJsonObject(),"uuid"));try(var q=db.connection().prepareStatement("SELECT player,about,interests FROM community_profile WHERE player=ANY(?)")){q.setArray(1,db.connection().createArrayOf("text",ids.toArray()));try(var rows=q.executeQuery()){while(rows.next())for(var e:entries){var row=e.getAsJsonObject();if(Json.str(row,"uuid").equals(rows.getString(1))){row.addProperty("about",rows.getString(2));row.addProperty("interests",rows.getString(3));}}}}
 }
 void home(CommunityStore.Actor a,JsonObject out,JsonArray groups)throws Exception{
  var tasks=new JsonArray();try(var q=db.connection().prepareStatement("SELECT i.id,i.body,i.group_id,d.body->>'title' FROM community_group_items i JOIN documents d ON d.id=i.group_id JOIN community_relations r ON r.document=i.group_id AND r.kind='members' AND r.actor=? WHERE i.kind='task' AND i.body->>'assignee'=? AND i.body->>'status'<>'done' AND d.body->>'status'='open' ORDER BY nullif((i.body->>'dueAt')::bigint,0) ASC NULLS LAST,i.id LIMIT 3")){q.setString(1,a.id());q.setString(2,a.id());try(var rows=q.executeQuery()){while(rows.next()){var t=Json.parse(rows.getString(2));t.addProperty("id",rows.getString(1));t.addProperty("group",rows.getString(3));t.addProperty("groupName",rows.getString(4));tasks.add(t);}}}out.add("tasks",tasks);
  for(var entry:groups){var option=entry.getAsJsonObject();var group=store.get(Json.str(option,"value"));option.addProperty("membersCount",group.getAsJsonObject("members").size());var ids=new JsonArray();group.getAsJsonObject("members").keySet().forEach(ids::add);option.add("memberIds",ids);
   try(var q=db.connection().prepareStatement("SELECT body FROM community_documents WHERE section='events' AND body->>'group'=? AND body->>'status'='open' AND (body->>'startsAt')::bigint>? ORDER BY (body->>'startsAt')::bigint LIMIT 26")){q.setString(1,Json.str(option,"value"));q.setLong(2,System.currentTimeMillis());try(var rows=q.executeQuery()){while(rows.next()){var event=Json.parse(rows.getString(1));if(visible(a,event)){option.addProperty("nextEvent",Json.str(event,"title"));option.add("nextEventAt",event.get("startsAt"));break;}}}}
   if(store.manager(group,a))option.addProperty("applicationsCount",group.getAsJsonObject("applications").size());}
 }
}
