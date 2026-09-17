package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import java.util.*;
import static dev.abros.anthub.client.CommunityScreen.*;
/** events entry layout and contextual actions. */
final class EventsSection {
 static void render(CommunityScreen host,JsonObject j){boolean manage=j.get("manage").getAsBoolean();
                if(can(j,"join")||can(j,"leave"))host.action(j.get("isParticipant").getAsBoolean()?"Отменить участие":"Участвовать",()->host.send(j.get("isParticipant").getAsBoolean()?"leave":"join",new JsonObject()));
                host.text("Начало: "+local(j.get("startsAt").getAsLong()));int capacity=j.get("capacity").getAsInt();var participants=j.getAsJsonObject("participants");host.text("Участников: "+j.get("participantsCount").getAsInt()+(capacity>0?" / "+capacity:""));int n=j.has("participantOffset")?j.get("participantOffset").getAsInt():0;for(var entry:participants.entrySet()){host.text((capacity>0&&n++>=capacity?"Очередь: ":"✓ ")+entry.getValue().getAsString());}
                if(j.has("waitlistPosition")&&j.get("waitlistPosition").getAsInt()>0)host.text("Ваше место в очереди: "+j.get("waitlistPosition").getAsInt());
                long remaining=j.get("startsAt").getAsLong()-System.currentTimeMillis();if(remaining>0)host.text("До начала: "+Math.max(1,(remaining+59999)/60000)+" мин.");

                if(can(j,"reschedule")){host.secondary("Перенести",()->host.form("Перенос события","reschedule",List.of(new CommunityScreen.Field("startsAt","Начало (местное время: yyyy-MM-dd HH:mm)",30)),new JsonObject()));host.secondary("Отменить событие",()->host.confirm("Отменить событие?","cancel",new JsonObject()));}

 }
}
