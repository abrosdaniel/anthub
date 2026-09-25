package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import java.util.*;
import net.minecraft.client.Minecraft;
final class CommunityTools {
 static JsonArray options(String...pairs){var out=new JsonArray();for(int i=0;i<pairs.length;i+=2){var row=new JsonObject();row.addProperty("value",pairs[i]);row.addProperty("label",pairs[i+1]);out.add(row);}return out;}
 static void creation(String section,List<CommunityScreen.Field> fields,JsonObject preset){
  if(section.equals("events")){
   fields.add(new CommunityScreen.Field("visibility","Кто видит событие",20,options("public","Все","group","Участники объединения","invited","Приглашённые")));
   fields.add(new CommunityScreen.Field("invitees","Приглашённые игроки",1200));
   fields.add(new CommunityScreen.Field("repeat","Повторение",20,options("none","Один раз","weekly","Каждую неделю","fortnightly","Раз в две недели","monthly","Каждый месяц")));
   fields.add(new CommunityScreen.Field("occurrences","Встреч в серии (2–26)",2));preset.addProperty("occurrences","8");preset.addProperty("timezone",java.time.ZoneId.systemDefault().getId());
  }
  if(section.equals("board")){
   fields.add(new CommunityScreen.Field("trade","Тип объявления",20,options("none","Обычное","buy","Куплю","sell","Продам","exchange","Обменяю")));
   fields.add(new CommunityScreen.Field("item","Предмет",100));fields.add(new CommunityScreen.Field("quantity","Количество",7));preset.addProperty("quantity","1");fields.add(new CommunityScreen.Field("terms","Условия обмена / оплаты",500));
  }
 }
 static void groupItems(CommunityScreen host,JsonObject group){
  String kind=host.groupTab.equals("tasks")?"task":"place";boolean manage=group.get("manage").getAsBoolean();
  if(manage){host.actionRow(List.of(new CommunityScreen.Row(kind.equals("task")?"+ Задача":"+ Место",()->edit(host,kind,null))));host.text("");}
  int found=0;
  if(group.has("groupItems"))for(var element:group.getAsJsonArray("groupItems")){var item=element.getAsJsonObject();if(!Json.str(item,"kind").equals(kind))continue;found++;int card=host.beginCard();var controls=new ArrayList<CommunityScreen.Row>();
   host.text(Json.str(item,"title"));String description=Json.opt(item,"description","");if(!description.isBlank())host.text(description);
   if(kind.equals("task")){
    host.text("Статус: "+switch(Json.str(item,"status")){case "done"->"Готово";case "working"->"В работе";default->"Открыта";});
    String assignee=Json.opt(item,"assignee","");host.text(assignee.isBlank()?"Ответственный не назначен":"Ответственный: "+host.shortName(assignee));long due=item.get("dueAt").getAsLong();if(due>0)host.text((due<System.currentTimeMillis()&&!Json.str(item,"status").equals("done")?"Просрочено · ":"Срок: ")+CommunityScreen.local(due));
    if(manage||assignee.equals(CommunityScreen.me()))controls.add(new CommunityScreen.Row("Статус…",()->Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),Json.str(item,"title"),List.of("Открыта","В работе","Готово"),i->{var b=identity(item);b.addProperty("status",List.of("open","working","done").get(i));host.send("plusTaskStatus",b);})))); 
   }else LocationActions.render(host,item);
   if(manage){controls.add(new CommunityScreen.Row("Изменить",()->edit(host,kind,item)));controls.add(new CommunityScreen.Row("Удалить",()->host.confirm("Удалить "+(kind.equals("task")?"задачу":"место")+"?","plusItemDelete",identity(item))));}
   host.actionRow(controls);
   host.endCard(card);
  }
  if(found==0)host.text(kind.equals("task")?"Задач пока нет. Они доступны участникам объединения.":"Мест пока нет.");
 }
 private static JsonObject identity(JsonObject item){var b=new JsonObject();b.addProperty("itemId",Json.str(item,"id"));b.add("itemRevision",item.get("revision"));return b;}
 private static void edit(CommunityScreen host,String kind,JsonObject item){
  var preset=new JsonObject();if(item!=null)for(String key:List.of("title","description","assignee","location"))if(item.has(key))preset.add(key,item.get(key).deepCopy());if(item!=null){preset.addProperty("itemId",Json.str(item,"id"));preset.add("itemRevision",item.get("revision"));long due=item.has("dueAt")?item.get("dueAt").getAsLong():0;preset.addProperty("dueAt",due==0?"":java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").format(java.time.Instant.ofEpochMilli(due).atZone(java.time.ZoneId.systemDefault())));}
  preset.addProperty("kind",kind);var fields=new ArrayList<CommunityScreen.Field>();fields.add(new CommunityScreen.Field("title","Название",kind.equals("place")?80:100));fields.add(new CommunityScreen.Field("description","Описание",1000));
  if(kind.equals("task")){fields.add(new CommunityScreen.Field("assignee","Ответственный",100));fields.add(new CommunityScreen.Field("dueAt","Срок (необязательно)",30));}
  host.form(item==null?kind.equals("task")?"Новая задача":"Новое место":"Редактировать", "plusItemSave",fields,preset);
 }
}
