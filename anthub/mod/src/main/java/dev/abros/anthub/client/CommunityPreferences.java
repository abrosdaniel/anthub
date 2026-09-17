package dev.abros.anthub.client;

import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

    final class CommunityPreferences extends ScrollScreen implements CommunityScreen.Receiver {
        private final CommunityScreen parent;private final JsonArray favorites=new JsonArray(),muted=new JsonArray();private String error="",request="";private boolean busy;private final dev.abros.anthub.core.RequestSession session=new dev.abros.anthub.core.RequestSession();
        CommunityPreferences(CommunityScreen parent){super(Component.literal("Настройки меню"));this.parent=parent;var prefs=parent.data.has("preferences")?parent.data.getAsJsonObject("preferences"):new JsonObject();if(prefs.has("favorites"))prefs.getAsJsonArray("favorites").forEach(favorites::add);if(prefs.has("muted"))prefs.getAsJsonArray("muted").forEach(muted::add);}
        private void toggle(JsonArray a,String k){var v=new JsonPrimitive(k);if(a.contains(v))a.remove(v);else a.add(v);rebuildWidgets();}
        @Override protected void init(){var keys=List.of("board","groups","events","polls","ideas","notices","sound","restartNotices");scrollArea(keys.size(),42,height-94,46,width-16);for(int n=firstRow;n<Math.min(keys.size(),firstRow+visibleRows);n++){String k=keys.get(n);int y=42+(n-firstRow)*46;if(n>=5){int setting=n==6?1:n==7?2:0;addRenderableWidget(Button.builder(Client.tr("server."+k).copy().append(": ").append(Client.tr(ServerMenuClient.enabled(setting)?"server.on":"server.off")),b->{ServerMenuClient.toggle(setting);rebuildWidgets();}).bounds(20,y,width-40,20).build());continue;}addRenderableWidget(Button.builder(Component.literal((favorites.contains(new JsonPrimitive(k))?"★ ":"☆ ")+CommunityScreen.name(k)),b->toggle(favorites,k)).bounds(20,y,width/2-25,20).build());addRenderableWidget(Button.builder(Component.literal(muted.contains(new JsonPrimitive(k))?"Без плашек":"Плашки включены"),b->toggle(muted,k)).bounds(width/2+5,y,width/2-25,20).build());}addRenderableWidget(Button.builder(Component.literal("Сохранить"),b->{if(busy)return;busy=true;request=UUID.randomUUID().toString();var j=new JsonObject();j.addProperty("action","community");j.addProperty("op","preferences");j.addProperty("section","home");j.addProperty("request",request);var p=new JsonObject();p.add("favorites",favorites);p.add("muted",muted);j.add("preferences",p);j=session.begin(j,true,System.currentTimeMillis());request=Json.str(j,"request");ServerMenuClient.request(j);}).bounds(20,height-28,110,20).build());addRenderableWidget(Button.builder(Component.literal("Клавиша меню"),b->minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options))).bounds(20,height-54,140,20).build());addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(width-100,height-28,80,20).build());}
        public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error")){error=Json.opt(j,"text","");return;}minecraft.setScreen(parent);parent.invalidate();}
        @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,14,0xE2BE75);g.drawString(font,error,20,height-55,0xEEEEEE);}
        @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;error="Нет ответа. Повторите сохранение.";}}
        @Override public void onClose(){session.cancel();parent.invalidate();minecraft.setScreen(parent.surface());}
        @Override public boolean isPauseScreen(){return false;}
    }
