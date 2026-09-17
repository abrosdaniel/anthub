package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;
final class PlayerActionsScreen extends ScrollScreen {
 private record Action(String label,Runnable run){}
 private boolean moderationOnly;private final Screen parent;private final JsonObject player;private JsonArray actions;private boolean resetPermission;
 private String reason="",minutes="10",status="";private final List<Action> rows=new ArrayList<>();
 PlayerActionsScreen(Screen parent,JsonObject player,JsonArray actions){super(PlayerText.name(player));this.parent=parent;this.player=player;this.actions=actions;}
 PlayerActionsScreen(Screen parent,JsonObject player,JsonArray actions,boolean moderationOnly){this(parent,player,actions);this.moderationOnly=moderationOnly;}
 @Override protected void init(){rows.clear();resetPermission=ServerMenuClient.state.has("authReset")&&ServerMenuClient.state.get("authReset").getAsBoolean();if(ServerMenuClient.state.has("actions"))actions=ServerMenuClient.state.getAsJsonArray("actions").deepCopy();boolean online=player.has("online")&&player.get("online").getAsBoolean();
  if(!moderationOnly){if(AuthClient.available()&&ServerMenuClient.state.has("authReset")&&ServerMenuClient.state.get("authReset").getAsBoolean())rows.add(new Action("Приглашение для сброса пароля",()->AuthAccountScreen.invite(this,Json.str(player,"name"))));
  if(online&&actions.contains(new JsonPrimitive("tell")))rows.add(new Action("Написать",()->minecraft.setScreen(new ChatScreen("/tell "+Json.str(player,"name")+" "))));
  if(!Json.str(player,"uuid").equals(Json.opt(ServerMenuClient.state,"uuid","")))rows.add(new Action("Пригласить в объединение",()->CommunityScreen.invite(this,player)));
  rows.add(new Action("Объединения игрока",()->CommunityScreen.groupsFor(this,Json.str(player,"uuid"))));
  rows.add(new Action(Client.tr("server.report").getString(),()->minecraft.setScreen(new ReportScreen(this,"Игрок: "+Json.str(player,"name")+"\n"))));
  }var available=new ArrayList<String>();for(var entry:actions){String action=entry.getAsString();if(action.equals("tell"))continue;if(online||Set.of("ban","pardon").contains(action))available.add(action);}
  int reasonRow=-1,minutesRow=-1;if(!available.isEmpty()){reasonRow=rows.size();rows.add(new Action("reason",null));if(available.contains("vmute")){minutesRow=rows.size();rows.add(new Action("minutes",null));}for(String action:available)rows.add(new Action(Client.tr("server.command."+action).getString(),()->run(action)));}
  int w=Math.min(500,width-40),x=(width-w)/2;scrollArea(rows.size(),44,height-60,30,x+w+4);
  for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++){int y=44+(i-firstRow)*30;if(i==reasonRow){var edit=addRenderableWidget(new EditBox(font,x,y,w,20,Client.tr("server.reason")));edit.setMaxLength(300);edit.setHint(Client.tr("server.reason"));edit.setValue(reason);edit.setResponder(v->reason=v);}else if(i==minutesRow){var edit=addRenderableWidget(new EditBox(font,x,y,70,20,Client.tr("server.muteMinutes")));edit.setMaxLength(5);edit.setFilter(v->v.matches("[0-9]*"));edit.setValue(minutes);edit.setResponder(v->minutes=v);}else{var action=rows.get(i);addRenderableWidget(Button.builder(Component.literal(action.label),b->action.run.run()).bounds(x,y,w,20).build());}}
  addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width/2-100,height-26,200,20).build());
 }
 private void run(String action){var j=new JsonObject();j.addProperty("action","moderate");j.addProperty("operation",action);j.addProperty("target",Json.str(player,"uuid"));j.addProperty("reason",reason);
  if((action.equals("kick")||action.equals("ban"))&&reason.isBlank()){status=Client.tr("server.reason").getString();return;}
  if(action.equals("vmute"))try{int duration=Integer.parseInt(minutes);if(duration<1||duration>10080)throw new NumberFormatException();j.addProperty("minutes",duration);}catch(NumberFormatException ex){status=Client.tr("server.invalidnumber").getString();return;}
  Component summary=title.copy().append("\n").append(reason);if(action.equals("vmute"))summary=summary.copy().append(" · "+minutes+" min");
  minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes){ServerMenuClient.result="";ServerMenuClient.request(j);}},Client.tr("server.command."+action),summary));
 }
 @Override public void tick(){if(!ServerMenuClient.available()){minecraft.setScreen(null);return;}boolean reset=ServerMenuClient.state.has("authReset")&&ServerMenuClient.state.get("authReset").getAsBoolean();if(reset!=resetPermission||ServerMenuClient.state.has("actions")&&!actions.equals(ServerMenuClient.state.getAsJsonArray("actions")))rebuildWidgets();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){g.fill(0,0,width,height,0xBB090E14);int w=Math.min(520,width-24),left=(width-w)/2;g.fill(left,6,left+w,height-8,0xF51B252E);}
 @Override public void render(GuiGraphics g,int x,int y,float d){
  if(parent!=null){if(parent.width!=width||parent.height!=height)parent.resize(minecraft,width,height);parent.render(g,-10000,-10000,d);}
  g.flush();g.pose().pushPose();
  try{
   g.pose().translate(0,0,400);super.render(g,x,y,d);
   var heading=net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(title,Math.min(500,width-40)));
   g.drawString(font,heading,(width-font.width(heading))/2,16,0xE2BE75);
   g.drawCenteredString(font,Component.literal(player.has("online")&&player.get("online").getAsBoolean()?"В сети":"Не в сети"),width/2,30,0xAAAAAA);
   for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++)if(rows.get(i).label.equals("minutes"))g.drawString(font,Client.tr("server.muteMinutes"),(width-Math.min(500,width-40))/2+78,50+(i-firstRow)*30,0xEEEEEE);
   Ui.status(g,font,status.isEmpty()?ServerMenuClient.result:status,(width-Math.min(500,width-40))/2,height-55,Math.min(500,width-40),height-30);
   g.flush();
  }finally{g.pose().popPose();}
 }
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){if(parent instanceof FeatureListScreen screen)screen.invalidate();minecraft.setScreen(parent);}
}
