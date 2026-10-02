package dev.abros.anthub.core;

import com.google.gson.*;
import java.util.*;

/** Bounded actor-scoped HUD read model; no subscriptions or screen state required. */
final class CommunityHud {
 private final PgDatabase db; private final CommunityStore store;
 CommunityHud(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 JsonObject snapshot(CommunityStore.Actor actor,JsonObject input,JsonObject config)throws Exception {
  long now=System.currentTimeMillis(),since=input.has("since")?input.get("since").getAsBigDecimal().longValueExact():0;
  if(since<0)throw new IllegalArgumentException("Invalid notification cursor");
  var out=new JsonObject();out.addProperty("kind","hud");out.addProperty("request",Json.opt(input,"request",""));out.addProperty("unread",store.unread(actor.id()));out.add("preferences",store.preferences(actor.id()));
  var groups=new JsonArray();for(var group:store.memberGroups(actor.id())){var row=new JsonObject();row.addProperty("id",Json.str(group,"id"));row.addProperty("title",Json.str(group,"title"));groups.add(row);}out.add("groups",groups);
  String pin=Json.opt(input,"pin","");if(pin.length()>100)throw new IllegalArgumentException("Invalid task pin");
  // A pin never expands visibility: only own or assigned tasks in an active membership qualify.
  var tasks=new JsonArray();try(var q=db.connection().prepareStatement("SELECT i.id,i.body,i.group_id FROM community_group_items i LEFT JOIN documents d ON d.id=i.group_id WHERE i.kind='task' AND i.body->>'status'<>'done' AND ((i.group_id IS NULL AND i.owner=?) OR (i.body->>'assignee'=? AND d.body->>'status'='open' AND EXISTS(SELECT 1 FROM community_relations r WHERE r.document=i.group_id AND r.kind='members' AND r.actor=?))) ORDER BY (i.id=?) DESC,nullif((i.body->>'dueAt')::bigint,0) ASC NULLS LAST,i.id LIMIT 1")){q.setString(1,actor.id());q.setString(2,actor.id());q.setString(3,actor.id());q.setString(4,pin);try(var rows=q.executeQuery()){if(rows.next()){var source=Json.parse(rows.getString(2));var task=new JsonObject();for(String k:List.of("title","dueAt","status"))if(source.has(k))task.add(k,source.get(k));task.addProperty("id",rows.getString(1));task.addProperty("group",Objects.toString(rows.getString(3),""));int done=0,total=0;if(source.has("subtasks"))for(var step:source.getAsJsonArray("subtasks")){total++;if(step.getAsJsonObject().has("done")&&step.getAsJsonObject().get("done").getAsBoolean())done++;}task.addProperty("done",done);task.addProperty("total",total);tasks.add(task);}}}out.add("tasks",tasks);
  var filter=new JsonObject();filter.addProperty("participating",true);var events=new CommunityQueries(db).list(actor,filter,"home",now,config);var nearest=new JsonArray();if(!events.isEmpty())nearest.add(events.get(0));out.add("events",nearest);
  long cap;try(var q=db.connection().prepareStatement("SELECT coalesce(max(id),0) FROM notices WHERE recipient=?")){q.setString(1,actor.id());try(var rows=q.executeQuery()){rows.next();cap=rows.getLong(1);}}
  var notices=new JsonArray();long next=since;boolean initial=!input.has("since");
  if(!initial)try(var q=db.connection().prepareStatement("SELECT id,body,read FROM notices WHERE recipient=? AND id>? AND id<=? ORDER BY id ASC LIMIT 51")){q.setString(1,actor.id());q.setLong(2,since);q.setLong(3,cap);try(var rows=q.executeQuery()){while(rows.next()){if(notices.size()==50)break;next=rows.getLong(1);var notice=Json.parse(rows.getString(2));notice.addProperty("id",Long.toString(next));notice.addProperty("read",rows.getBoolean(3));notices.add(notice);}}}
  out.add("notices",notices);out.addProperty("sequence",initial?cap:Math.max(next,Math.min(since,cap)));out.addProperty("more",!initial&&next<cap);return out;
 }
}
