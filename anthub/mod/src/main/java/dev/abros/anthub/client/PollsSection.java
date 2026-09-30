package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import java.util.*;
import static dev.abros.anthub.client.CommunityScreen.*;
/** polls entry layout and contextual actions. */
final class PollsSection {
 static void render(CommunityScreen host,JsonObject j){boolean manage=j.get("manage").getAsBoolean();
                host.text("До: "+local(j.get("endsAt").getAsLong()));var options=j.getAsJsonArray("options");var counts=j.getAsJsonArray("counts");int maxCount=1;for(var count:counts)maxCount=Math.max(maxCount,count.getAsInt());
                for(int i=0;i<options.size();i++){
                 int index=i,count=counts.get(i).getAsInt();String label=options.get(i).getAsString()+(count<0?"":" · "+count);
                 Runnable choose=j.get("endsAt").getAsLong()<=System.currentTimeMillis()||j.get("voted").getAsBoolean()&&!j.get("changeVote").getAsBoolean()?null:()->{host.choicesDirty=true;boolean selected=host.choices.contains(index);if(!j.get("multiple").getAsBoolean())host.choices.clear();if(selected)host.choices.remove(index);else host.choices.add(index);host.rows.clear();host.detail(j);host.refreshUi();};
                 host.rows.add(new CommunityScreen.Row(label,choose,count<0?-1:(double)count/maxCount,List.of(),host.choices.contains(i)));
                }
                if(can(j,"vote"))host.action("Голосовать",()->{if(host.choices.isEmpty()){host.status="Выберите вариант ответа";return;}var body=new JsonObject();var selected=new JsonArray();host.choices.forEach(selected::add);body.add("choices",selected);host.send("vote",body);});

 }
}
