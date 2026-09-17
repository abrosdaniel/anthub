package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import java.util.*;
import static dev.abros.anthub.client.CommunityScreen.*;
/** ideas entry layout and contextual actions. */
final class IdeasSection {
 static void render(CommunityScreen host,JsonObject j){boolean manage=j.get("manage").getAsBoolean();host.text("Поддержали: "+j.get("supportersCount").getAsInt());if(can(j,"support"))host.action(j.getAsJsonObject("supporters").has(me())?"Убрать поддержку":"Поддержать",()->host.send("support",new JsonObject()));host.text("Статус: "+status(Json.str(j,"status")));if(!Json.opt(j,"answer","").isBlank())host.text("\nОтвет администрации\n"+Json.str(j,"answer"));if(can(j,"status"))host.secondary("Изменить статус и ответить…",()->{var states=List.of("new","discussion","planned","done","declined");net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),"Решение по предложению",states.stream().map(CommunityScreen::status).toList(),i->{var body=new JsonObject();body.addProperty("status",states.get(i));host.form("Официальный ответ","status",List.of(new CommunityScreen.Field("text","Объяснение",1000)),body);}));});
 }
}
