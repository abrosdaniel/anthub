package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;
final class ModerationVoteScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private JsonObject target,vote=new JsonObject();private boolean creating,busy,started;private String reason="",measure="kick",status="",request="";private long sent,lastRefresh;private final List<String> lines=new ArrayList<>();
 ModerationVoteScreen(Screen parent,JsonObject target){super(Component.literal("Нарушение правил"));this.parent=parent;this.target=target;creating=target!=null;}
 static boolean enabled(){return ServerMenuClient.supports("moderation-votes")&&ServerMenuClient.state.has("moderationVotes")&&ServerMenuClient.state.getAsJsonObject("moderationVotes").get("enabled").getAsBoolean();}
 private JsonObject config(){return ServerMenuClient.state.has("moderationVotes")?ServerMenuClient.state.getAsJsonObject("moderationVotes"):new JsonObject();}
 private String setting(String key,String fallback){return config().has(key)?config().get(key).getAsString():fallback;}
 private static String measureName(String key){return switch(key){case "kick"->"Кик";case "ban"->"Временный бан";case "mute"->"Голосовой mute";default->key;};}
 @Override protected void init(){int w=Math.min(480,width-28),left=(width-w)/2;lines.clear();
  if(creating){
   var allowed=config().has("actions")?config().getAsJsonArray("actions"):new JsonArray();if(!allowed.contains(new JsonPrimitive(measure)))measure=allowed.isEmpty()?"":allowed.get(0).getAsString();
   addRenderableWidget(Button.builder(Component.literal("Игрок: "+(target==null?"выбрать":Json.str(target,"name"))),b->FeatureListScreen.pick(this,p->{target=p;rebuildWidgets();})).bounds(left,40,w,20).build());
   addRenderableWidget(Button.builder(Component.literal("Мера: "+measureName(measure)),b->{var actions=new ArrayList<String>();for(var action:config().getAsJsonArray("actions"))actions.add(action.getAsString());minecraft.setScreen(new ChoicePopup(this,"Мера",actions.stream().map(ModerationVoteScreen::measureName).toList(),i->{measure=actions.get(i);rebuildWidgets();}));}).bounds(left,66,w,20).build());
   var edit=addRenderableWidget(new EditBox(font,left,92,w,20,Component.literal("Причина")));edit.setHint(Component.literal("Причина нарушения"));edit.setMaxLength(300);edit.setValue(reason);edit.setResponder(v->reason=v);
   String conditions="Минимум участников: "+setting("minimumPlayers","5")+". Длительность: "+setting("durationSeconds","120")+" с. За: не менее 2/3 проголосовавших и половины участников. Бан: "+setting("banMinutes","30")+" мин; голосовой mute: "+setting("muteMinutes","15")+" мин.";
   for(var line:font.getSplitter().splitLines(conditions,w,net.minecraft.network.chat.Style.EMPTY))lines.add(line.getString());
   var create=addRenderableWidget(Button.builder(Component.literal("Начать голосование…"),b->confirm()).bounds(left,height-56,w,20).build());create.active=!busy&&enabled()&&!measure.isEmpty();
  }else{
   if(vote.has("id")){String body=Json.str(vote,"name")+" · "+measureName(Json.str(vote,"action"))+"\n"+Json.str(vote,"reason")+"\nЗа: "+vote.get("yes")+" · Против: "+vote.get("no")+" · Участников: "+vote.get("eligibleCount")+"\n"+Json.opt(vote,"outcome",Json.str(vote,"status").equals("open")?"Голосование идёт":"Применение результата…");for(String paragraph:body.split("\n"))for(var line:font.getSplitter().splitLines(paragraph,w,net.minecraft.network.chat.Style.EMPTY))lines.add(line.getString());
    if(Json.str(vote,"status").equals("open")){
     boolean allowed=vote.get("canVote").getAsBoolean()&&!busy;
     var yes=addRenderableWidget(Button.builder(Component.literal(vote.has("myVote")&&vote.get("myVote").getAsBoolean()?"✓ За":"За"),b->ballot(true)).bounds(left,height-82,w/2-3,20).build());yes.active=allowed;
     var no=addRenderableWidget(Button.builder(Component.literal(vote.has("myVote")&&!vote.get("myVote").getAsBoolean()?"✓ Против":"Против"),b->ballot(false)).bounds(left+w/2+3,height-82,w/2-3,20).build());no.active=allowed;
     if(ServerMenuClient.admin())addRenderableWidget(Button.builder(Component.literal("Отменить с причиной…"),b->minecraft.setScreen(new CancelForm(this))).bounds(left,height-56,w,20).build());
    }else addCreate(left,w);
   }else{lines.add("Сейчас нет голосования о нарушении правил.");addCreate(left,w);}
   scrollArea(lines.size(),42,height-110,14,left+w+4);
  }
  addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(left,height-28,w,20).build());if(!started){started=true;if(!creating)send("view",new JsonObject());}
 }
 private void addCreate(int x,int w){if(enabled())addRenderableWidget(Button.builder(Component.literal("Новое голосование"),b->{creating=true;resetScroll();rebuildWidgets();}).bounds(x,height-56,w,20).build());}
 private void confirm(){if(target==null||reason.isBlank()){status="Выберите игрока и укажите причину";return;}var j=new JsonObject();j.addProperty("target",Json.str(target,"uuid"));j.addProperty("measure",measure);j.addProperty("reason",reason);minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)send("start",j);},Component.literal("Начать голосование?"),Component.literal(Json.str(target,"name")+" · "+measureName(measure)+"\n"+reason)));}
 private void ballot(boolean yes){var j=new JsonObject();j.addProperty("id",Json.str(vote,"id"));j.addProperty("yes",yes);send("vote",j);}
 private void send(String op,JsonObject j){if(busy)return;busy=true;sent=lastRefresh=System.currentTimeMillis();request=UUID.randomUUID().toString();j.addProperty("action","moderationVote");j.addProperty("op",op);j.addProperty("request",request);if(op.equals("start")){j.addProperty("operationId",UUID.randomUUID().toString());j.addProperty("issuedAt",sent);}ServerMenuClient.request(j);status="";}
 @Override public void receiveCommunity(JsonObject j){if(!request.equals(Json.opt(j,"request","")))return;busy=false;if(j.has("error")){status=Json.opt(j,"text","Действие не выполнено");return;}if(j.has("vote")){vote=j.getAsJsonObject("vote");creating=false;rebuildWidgets();}}
 @Override public void tick(){if(!ServerMenuClient.available()){onClose();return;}if(busy&&System.currentTimeMillis()-sent>15000){busy=false;status="Нет ответа. Закройте и откройте голосование повторно.";lastRefresh=System.currentTimeMillis();}if(!creating&&!busy&&System.currentTimeMillis()-lastRefresh>5000)send("view",new JsonObject());}
 @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);int w=Math.min(480,width-28),left=(width-w)/2;g.drawCenteredString(font,title,width/2,16,0xE2BE75);if(creating){g.enableScissor(left,122,left+w,height-82);for(int i=0;i<lines.size();i++)g.drawString(font,lines.get(i),left,122+i*14,0xEEEEEE);g.disableScissor();}else{for(int i=firstRow;i<Math.min(lines.size(),firstRow+visibleRows);i++)g.drawString(font,lines.get(i),left,42+(i-firstRow)*14,0xEEEEEE);if(vote.has("endsAt")&&Json.opt(vote,"status","").equals("open"))g.drawString(font,"Осталось: "+Math.max(0,(vote.get("endsAt").getAsLong()-System.currentTimeMillis()+999)/1000)+" с",left,height-106,0xE2BE75);}Ui.status(g,font,status,left,height-105,w,height-84);}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
 private static final class CancelForm extends Screen{
  private final ModerationVoteScreen parent;private EditBox reason;
  CancelForm(ModerationVoteScreen parent){super(Component.literal("Причина отмены"));this.parent=parent;}
  @Override protected void init(){String draft=reason==null?"":reason.getValue();int w=Math.min(400,width-24),x=(width-w)/2;reason=addRenderableWidget(new EditBox(font,x,60,w,20,title));reason.setMaxLength(300);reason.setValue(draft);addRenderableWidget(Button.builder(Component.literal("Отменить голосование"),b->{if(reason.getValue().isBlank())return;var j=new JsonObject();j.addProperty("id",Json.str(parent.vote,"id"));j.addProperty("reason",reason.getValue());minecraft.setScreen(parent);parent.send("cancel",j);}).bounds(x,90,w,20).build());addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(x,height-28,w,20).build());setInitialFocus(reason);}
  @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,30,0xE2BE75);}
  @Override public void onClose(){minecraft.setScreen(parent);}
 }
}
