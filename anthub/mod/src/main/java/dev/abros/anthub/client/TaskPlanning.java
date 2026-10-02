package dev.abros.anthub.client;
import com.google.gson.*;import dev.abros.anthub.core.Json;import java.util.*;
/** Task planning presentation, separate from workspace and request coordination. */
final class TaskPlanning {static void rows(TaskScreen host,JsonObject t){
  var deps=t.has("dependencyDetails")?t.getAsJsonArray("dependencyDetails"):new JsonArray();
  if(!deps.isEmpty())host.row("Сначала выполнить",null);
  for(var e:deps){var d=e.getAsJsonObject();String code=Json.opt(d,"code","");host.paragraph((d.get("done").getAsBoolean()?"Готово: ":"Ожидается: ")+code+" · "+Json.str(d,"title"));}
  int days=t.has("repeatDays")?t.get("repeatDays").getAsInt():0;var intervals=List.of(0,1,7,30);var labels=List.of("Не повторять","Каждый день","Каждые 7 дней","Каждые 30 дней");
  if(days>0)host.row("Повтор: "+labels.get(Math.max(0,intervals.indexOf(days))),null);
  if(days>0)host.paragraph("Следующая отдельная задача: "+CommunityScreen.local(t.get("nextRepeatAt").getAsLong()));
 }
 private TaskPlanning(){}
}
