package dev.abros.anthub.core;
import com.google.gson.*;
import java.util.*;
/** Action availability shared by the response model and command authorization. */
public final class CommunityPolicy {
 private CommunityPolicy(){}
 public static JsonArray actions(JsonObject item,CommunityStore.Actor actor,long now){
  var actions=new LinkedHashSet<String>();String section=Json.str(item,"section"),status=Json.str(item,"status");boolean owner=actor.id().equals(Json.str(item,"owner"));
  boolean member=section.equals("groups")&&item.getAsJsonObject("members").has(actor.id());
  boolean manager=actor.admin()||owner||member&&"assistant".equals(item.getAsJsonObject("members").get(actor.id()).getAsString());
  if(status.equals("deleted")){var result=new JsonArray();if((owner||actor.admin())&&item.get("deletedAt").getAsLong()+30L*86400000>now)result.add("restore");return result;}
  if(owner||actor.admin())actions.add("delete");
  if(status.equals("hidden")){var result=new JsonArray();actions.forEach(result::add);return result;}
  if(owner||actor.admin()||section.equals("groups")&&manager)actions.add("location");
  if((owner||actor.admin())&&(!section.equals("polls")||item.getAsJsonObject("votes").isEmpty()))actions.add("edit");
  if(section.equals("board")&&item.getAsJsonObject("responses").has(actor.id()))actions.add("withdrawResponse");
  if(section.equals("groups")){
   if(item.getAsJsonObject("applications").has(actor.id()))actions.add("withdrawApplication");
   if(manager&&!item.getAsJsonObject("invitations").isEmpty())actions.add("revokeInvitation");
  }
  switch(section){
   case "board" -> {if(status.equals("open")){if(owner)Collections.addAll(actions,"close","complete","respondDecision");else if(item.get("endsAt").getAsLong()>now&&!item.getAsJsonObject("responses").has(actor.id()))actions.add("respond");}}
   case "groups" -> {if(status.equals("open")){if(manager)Collections.addAll(actions,"application","invite","recruiting");if(owner)actions.add("role");if(member&&!owner)actions.add("leave");if(!member&&item.get("recruiting").getAsBoolean()&&!item.getAsJsonObject("applications").has(actor.id()))actions.add("apply");if(item.getAsJsonObject("invitations").has(actor.id()))actions.add("invitation");}}
   case "events" -> {if(status.equals("open")&&item.get("startsAt").getAsLong()>now){actions.add(item.getAsJsonObject("participants").has(actor.id())?"leave":"join");if(owner||actor.admin())Collections.addAll(actions,"reschedule","cancel");}}
   case "polls" -> {if(status.equals("open")&&item.get("endsAt").getAsLong()>now&&(!item.getAsJsonObject("votes").has(actor.id())||item.get("changeVote").getAsBoolean()))actions.add("vote");}
   case "ideas" -> {if(!Set.of("done","declined").contains(status))actions.add("support");if(actor.admin())actions.add("status");}
  }
  if(actor.admin())actions.add("hide");var out=new JsonArray();actions.forEach(out::add);return out;
 }
 public static void check(JsonObject item,CommunityStore.Actor actor,String operation,long now){if(!actions(item,actor,now).contains(new JsonPrimitive(operation)))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Действие больше недоступно. Обновите запись.");}
}
