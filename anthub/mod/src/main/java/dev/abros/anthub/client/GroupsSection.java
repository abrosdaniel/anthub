package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import java.util.*;
import static dev.abros.anthub.client.CommunityScreen.*;
/** Overview, roster and private management queues share one selected group. */
final class GroupsSection {
 static void render(CommunityScreen host,JsonObject j){
  boolean manage=j.get("manage").getAsBoolean();
  if(!manage&&host.groupTab.equals("requests"))host.groupTab="overview";
  if(host.groupTab.equals("members")){
   host.text("Участников: "+j.get("membersCount").getAsInt());
   for(var entry:j.getAsJsonObject("members").entrySet()){
    String target=entry.getKey(),role=entry.getValue().getAsString();String label=host.shortName(target)+" · "+switch(role){case "leader"->"Руководитель";case "assistant"->"Помощник";default->"Участник";};
    if(host.owner(j)&&!target.equals(me()))host.action(label+"  ⋯",()->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),host.shortName(target),List.of("Сделать участником","Назначить помощником","Передать руководство"),i->{var body=new JsonObject();body.addProperty("target",target);body.addProperty("role",List.of("member","assistant","leader").get(i));host.confirm(i==2?"Передать руководство этому участнику?":"Изменить роль участника?","role",body);})));else host.text(label);
   }
  }else if(host.groupTab.equals("requests")){
   var applications=j.getAsJsonObject("applications");host.text("Заявки · "+j.get("applicationsCount").getAsInt());if(applications.isEmpty())host.text("Нет ожидающих заявок.");
   for(var entry:applications.entrySet()){String target=entry.getKey();var application=entry.getValue().getAsJsonObject();host.text(Json.str(application,"name")+"\n"+Json.str(application,"text"));host.action("Рассмотреть: "+Json.str(application,"name"),()->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),"Заявка: "+Json.str(application,"name"),List.of("Принять","Отклонить"),i->{var b=new JsonObject();b.addProperty("target",target);b.addProperty("accept",i==0);host.send("application",b);})));}
   host.text("\nПриглашения · "+j.get("invitationsCount").getAsInt());
   if(j.getAsJsonObject("invitations").isEmpty())host.text("Нет ожидающих приглашений.");
   for(String target:j.getAsJsonObject("invitations").keySet())host.action(host.shortName(target)+" · отменить приглашение",()->{var b=new JsonObject();b.addProperty("target",target);host.confirm("Отменить приглашение?","revokeInvitation",b);});
  }else{
   host.text(Json.str(j,"type")+" · "+(j.get("recruiting").getAsBoolean()?"Набор открыт":"Набор закрыт"));
   host.text("Участников: "+j.get("membersCount").getAsInt());
   if(can(j,"invite")&&host.invitationTarget!=null)host.action("Пригласить "+Json.str(host.invitationTarget,"name"),()->{var invite=new JsonObject();invite.addProperty("target",Json.str(host.invitationTarget,"uuid"));host.send("invite",invite);});
   if(can(j,"apply"))host.action("Подать заявку",()->host.form("Заявка","apply",List.of(new CommunityScreen.Field("text","О себе",500)),new JsonObject()));
   if(can(j,"withdrawApplication")){host.text("Ваша заявка ожидает рассмотрения.");host.secondary("Отозвать мою заявку",()->host.confirm("Отозвать заявку?","withdrawApplication",new JsonObject()));}
   if(j.getAsJsonObject("invitations").has(me()))host.action("Ответить на приглашение",()->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),"Приглашение в объединение",List.of("Принять","Отклонить"),i->{var b=new JsonObject();b.addProperty("accept",i==0);host.send("invitation",b);})));
  }
  if(can(j,"leave"))host.secondary("Выйти из объединения",()->host.confirm("Выйти из объединения?","leave",new JsonObject()));
  if(can(j,"invite"))host.secondary("Пригласить игрока",()->FeatureListScreen.pick(host.surface(),player->{var body=new JsonObject();body.addProperty("target",Json.str(player,"uuid"));host.send("invite",body);}));
  if(can(j,"recruiting"))host.secondary(j.get("recruiting").getAsBoolean()?"Закрыть набор":"Открыть набор",()->host.send("recruiting",new JsonObject()));
 }
}
