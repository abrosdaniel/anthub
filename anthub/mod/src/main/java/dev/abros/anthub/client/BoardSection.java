package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import java.util.*;
import static dev.abros.anthub.client.CommunityScreen.*;
/** board entry layout and contextual actions. */
final class BoardSection {
 static void render(CommunityScreen host,JsonObject j){boolean manage=j.get("manage").getAsBoolean();
                host.text("Действует до: "+local(j.get("endsAt").getAsLong()));if(!Json.opt(j,"category","").isEmpty())host.text("Категория: "+Json.str(j,"category"));
                var responses=j.getAsJsonObject("responses");if(can(j,"respond"))host.action("Откликнуться",()->host.form("Отклик","respond",List.of(new CommunityScreen.Field("text","Сообщение",1000)),new JsonObject()));
                for(var entry:responses.entrySet()){String target=entry.getKey();var reply=entry.getValue().getAsJsonObject();host.text("\n"+Json.str(reply,"name")+" · "+status(Json.str(reply,"status"))+"\n"+Json.str(reply,"text"));if(!Json.opt(reply,"answer","").isEmpty())host.text("Ответ: "+Json.str(reply,"answer"));if(can(j,"respondDecision"))host.action("Ответить: "+Json.str(reply,"name"),()->{var decisions=List.of("accepted","declined","pending");net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),"Ответ на отклик",decisions.stream().map(CommunityScreen::status).toList(),i->{var body=new JsonObject();body.addProperty("target",target);body.addProperty("decision",decisions.get(i));host.form("Ответ на отклик","respondDecision",List.of(new CommunityScreen.Field("text","Ответ",1000)),body);}));});}
                if(can(j,"withdrawResponse")){var own=responses.getAsJsonObject(me());boolean accepted=own!=null&&Json.opt(own,"status","").equals("accepted");host.secondary("Отозвать мой отклик",()->{var body=new JsonObject();body.addProperty("confirmAccepted",accepted);host.confirm(accepted?"Отклик принят. Отменить и уведомить автора?":"Отозвать отклик?","withdrawResponse",body);});}
                if(can(j,"close")){host.secondary("Закрыть объявление",()->host.confirm("Закрыть объявление?","close",new JsonObject()));host.secondary("Отметить выполненным",()->host.confirm("Отметить выполненным?","complete",new JsonObject()));}

 }
}
