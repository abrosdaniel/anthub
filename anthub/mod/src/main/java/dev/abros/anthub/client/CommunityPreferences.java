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
        private final Screen parent;private final JsonArray favorites=new JsonArray(),muted=new JsonArray();private String error="",request="";private boolean busy,retry;private final dev.abros.anthub.core.RequestSession session=new dev.abros.anthub.core.RequestSession();
        CommunityPreferences(Screen parent,JsonObject prefs){super(Component.literal("Настройки уведомлений"));this.parent=parent;if(prefs.has("favorites"))prefs.getAsJsonArray("favorites").forEach(favorites::add);if(prefs.has("muted"))prefs.getAsJsonArray("muted").forEach(muted::add);}
        private void toggle(JsonArray a,String k){var v=new JsonPrimitive(k);if(a.contains(v))a.remove(v);else a.add(v);rebuildWidgets();}
        private int panelTop(){return Math.max(12,(height-300)/2);}
        @Override protected void init(){int w=Math.min(440,width-32),x=(width-w)/2,top=panelTop();var keys=List.of("board","groups","events","polls","ideas","notices","sound","restartNotices");scrollArea(keys.size(),top+30,Math.min(height-66,top+244),26,x+w+4);for(int n=firstRow;n<Math.min(keys.size(),firstRow+visibleRows);n++){String k=keys.get(n);int y=top+30+(n-firstRow)*26;if(n>=5){int setting=n==6?1:n==7?2:0;addRenderableWidget(Button.builder(Component.literal((setting==0?"Все плашки":setting==1?"Звук уведомлений":"Предупреждения о перезапуске")+": "+(ServerMenuClient.enabled(setting)?"вкл":"выкл")),b->{ServerMenuClient.toggle(setting);rebuildWidgets();}).bounds(x,y,w,20).build());continue;}addRenderableWidget(Button.builder(Component.literal(CommunityScreen.name(k)+": "+(muted.contains(new JsonPrimitive(k))?"без плашек":"плашки включены")),b->toggle(muted,k)).bounds(x,y,w,20).build());}addRenderableWidget(Button.builder(Component.literal(busy?"Сохранение…":retry?"Повторить":"Сохранить"),b->{if(busy)return;busy=true;if(retry){var again=session.retry(System.currentTimeMillis());request=Json.str(again,"request");ServerMenuClient.request(again);rebuildWidgets();return;}request=UUID.randomUUID().toString();var j=new JsonObject();j.addProperty("action","community");j.addProperty("op","preferences");j.addProperty("section","home");j.addProperty("request",request);var p=new JsonObject();p.add("favorites",favorites);p.add("muted",muted);j.add("preferences",p);j=session.begin(j,true,System.currentTimeMillis());request=Json.str(j,"request");ServerMenuClient.request(j);rebuildWidgets();}).bounds(x,Math.min(height-28,top+272),w/2-3,20).build());addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x+w/2+3,Math.min(height-28,top+272),w/2-3,20).build());for(var child:children())if(child instanceof AbstractWidget widget)widget.active=!busy&&(!retry||widget.getMessage().getString().equals("Повторить")||widget.getMessage().getString().equals("Назад"));}
        public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error")){error=Json.opt(j,"text","");retry=!Set.of("INVALID","FORBIDDEN","EXPIRED","CONFLICT").contains(Json.opt(j,"code",""));rebuildWidgets();return;}minecraft.setScreen(parent);invalidateParent();}
        @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,panelTop()+6,0xE2BE75);g.drawString(font,error,20,height-55,0xEEEEEE);}
        @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;retry=true;error="Нет ответа. Повторите сохранение.";rebuildWidgets();}}
        @Override public void onClose(){if(busy)return;session.cancel();invalidateParent();minecraft.setScreen(parent);}
        private void invalidateParent(){if(parent instanceof NotificationPopup p)p.invalidate();else if(parent instanceof CommunityScreen p)p.invalidate();}
        @Override public boolean isPauseScreen(){return false;}
    }
