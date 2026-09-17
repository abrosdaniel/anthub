package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import java.util.*;
import static dev.abros.anthub.client.CommunityScreen.*;
/** polls entry layout and contextual actions. */
final class PollsSection {
 static void render(CommunityScreen host,JsonObject j){boolean manage=j.get("manage").getAsBoolean();
                host.text("До: "+local(j.get("endsAt").getAsLong()));var options=j.getAsJsonArray("options");var counts=j.getAsJsonArray("counts");int maxCount=1;for(var count:counts)maxCount=Math.max(maxCount,count.getAsInt());
                for(int i=0;i<options.size();i++){int index=i;String label=(host.choices.contains(i)?"☑ ":"☐ ")+options.get(i).getAsString();if(counts.get(i).getAsInt()>=0)label+=" · "+counts.get(i).getAsInt();if(j.get("endsAt").getAsLong()<=System.currentTimeMillis()||j.get("voted").getAsBoolean()&&!j.get("changeVote").getAsBoolean()){host.rows.add(new CommunityScreen.Row(label,null,counts.get(i).getAsInt()<0?-1:(double)counts.get(i).getAsInt()/maxCount));continue;}int rowIndex=host.rows.size();host.action(label,()->{host.choicesDirty=true;boolean selected=host.choices.contains(index);if(!j.get("multiple").getAsBoolean())host.choices.clear();if(selected)host.choices.remove(index);else host.choices.add(index);host.rows.clear();host.detail(j);host.refreshUi();});if(counts.get(i).getAsInt()>=0){var option=host.rows.get(rowIndex);host.rows.set(rowIndex,new CommunityScreen.Row(option.label(),option.click(),(double)counts.get(i).getAsInt()/maxCount));}}
                if(can(j,"vote"))host.action("Голосовать",()->{if(host.choices.isEmpty()){host.status="Выберите вариант ответа";return;}var body=new JsonObject();var selected=new JsonArray();host.choices.forEach(selected::add);body.add("choices",selected);host.send("vote",body);});

 }
}
