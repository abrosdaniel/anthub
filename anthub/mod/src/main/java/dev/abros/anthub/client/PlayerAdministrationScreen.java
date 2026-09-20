package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
final class PlayerAdministrationScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private final JsonObject player;private final List<String> paragraphs=new ArrayList<>(),lines=new ArrayList<>();
 private String request="",cursor="",status="";private boolean started,busy;private long sent;
 PlayerAdministrationScreen(Screen parent,JsonObject player){super(Component.literal("Права и история · "+Json.str(player,"name")));this.parent=parent;this.player=player;}
 @Override protected void init(){lines.clear();for(String paragraph:paragraphs)for(var line:font.getSplitter().splitLines(paragraph,Math.min(580,width-32),net.minecraft.network.chat.Style.EMPTY))lines.add(line.getString());scrollArea(lines.size(),38,height-58,14,(width+Math.min(580,width-32))/2+4);addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width/2-100,height-28,200,20).build());if(!started){started=true;load();}}
 private void load(){if(busy)return;busy=true;sent=System.currentTimeMillis();request=UUID.randomUUID().toString();var j=new JsonObject();j.addProperty("action","playerAdministration");j.addProperty("target",Json.str(player,"uuid"));j.addProperty("cursor",cursor);j.addProperty("request",request);ServerMenuClient.request(j);status="Загрузка…";}
 void receive(JsonObject j){if(!request.equals(Json.opt(j,"request","")))return;busy=false;status="";
  if(paragraphs.isEmpty()){var permissions=j.getAsJsonObject("permissions");paragraphs.add("Текущие разрешения LuckPerms");if(!permissions.has("available"))paragraphs.add("Нет доступных данных API для этого игрока");else for(var entry:permissions.getAsJsonObject("capabilities").entrySet())paragraphs.add(entry.getKey()+": "+(entry.getValue().getAsBoolean()?"разрешено":"не разрешено"));paragraphs.add(j.get("online").getAsBoolean()?"Доступные команды сейчас: "+j.get("actions"):"Команды офлайн-игрока: неизвестно");paragraphs.add("Наследование групп здесь не отображается.");paragraphs.add("История модерации");}
  var history=j.getAsJsonObject("history");for(var value:history.getAsJsonArray("entries")){var entry=value.getAsJsonObject();paragraphs.add(CommunityScreen.local(entry.get("at").getAsLong())+" · "+Json.str(entry,"actor")+" · "+Json.str(entry,"action"));paragraphs.add(Json.str(entry,"reason"));paragraphs.add(Json.str(entry,"outcome")+(entry.has("until")?" · до "+CommunityScreen.local(entry.get("until").getAsLong()):""));}
  cursor=Json.opt(history,"nextCursor","");if(history.getAsJsonArray("entries").isEmpty()&&paragraphs.size()<8)paragraphs.add("Записей пока нет");rebuildWidgets();
 }
 @Override public void receiveCommunity(JsonObject response){if(request.equals(Json.opt(response,"request",""))){busy=false;status=Json.opt(response,"text","Не удалось загрузить данные");}}
 @Override protected void onScrollEnd(){if(!cursor.isEmpty())load();}
 @Override public void tick(){if(!ServerMenuClient.admin())onClose();else if(busy&&System.currentTimeMillis()-sent>15000){busy=false;status="Нет ответа. Закройте и откройте карточку повторно.";}}
 @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,16,0xE2BE75);int left=(width-Math.min(580,width-32))/2;for(int i=firstRow;i<Math.min(lines.size(),firstRow+visibleRows);i++)g.drawString(font,lines.get(i),left,38+(i-firstRow)*14,0xEEEEEE);Ui.status(g,font,status,left,height-52,Math.min(580,width-32),height-30);}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
