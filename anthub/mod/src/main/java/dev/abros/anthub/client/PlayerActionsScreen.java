package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;
final class PlayerActionsScreen extends ScrollScreen {
 private record Action(String label,Runnable run,List<Action> pair){Action(String label,Runnable run){this(label,run,List.of());}}
 private boolean profileActions;private boolean moderationOnly;private final Screen parent;private final JsonObject player;private JsonArray actions;private boolean resetPermission;
 private int panelTop=6,panelBottom;
 private String reason="",minutes="10",status="",banMinutes="0";private final List<Action> rows=new ArrayList<>();
 PlayerActionsScreen(Screen parent,JsonObject player,JsonArray actions){super(PlayerText.name(player));this.parent=parent;this.player=player;this.actions=actions;}
 PlayerActionsScreen(Screen parent,JsonObject player,JsonArray actions,boolean moderationOnly){this(parent,player,actions);this.moderationOnly=moderationOnly;}
 private void pair(Action a,Action b){rows.add(new Action("actions",null,List.of(a,b)));}
 @Override protected void init(){rows.clear();resetPermission=ServerMenuClient.state.has("authReset")&&ServerMenuClient.state.get("authReset").getAsBoolean();if(ServerMenuClient.state.has("actions"))actions=ServerMenuClient.state.getAsJsonArray("actions").deepCopy();boolean online=player.has("online")&&player.get("online").getAsBoolean();boolean self=Json.str(player,"uuid").equals(Json.opt(ServerMenuClient.state,"uuid",""));
  if(moderationOnly){if(ServerMenuClient.admin()&&ServerMenuClient.supports("admin-tools"))rows.add(new Action("Права и история модерации",()->minecraft.setScreen(new PlayerAdministrationScreen(this,player))));if(AuthClient.available()&&resetPermission)rows.add(new Action("Сброс пароля…",()->AuthAccountScreen.invite(this,Json.str(player,"name"))));}
  else{
   if(!profileActions){
   var bio=PlayerStatisticsText.biography(player,Math.min(340,width-40));for(int i=0;i<bio.size();i+=2){var paragraph=new ArrayList<Action>();for(int n=i;n<Math.min(i+2,bio.size());n++)paragraph.add(new Action(bio.get(n),null));rows.add(new Action("bio",null,paragraph));}
   var stats=PlayerStatisticsText.statistics(player);for(int i=0;i<stats.size();i+=2){var tiles=new ArrayList<Action>();for(int n=i;n<Math.min(i+2,stats.size());n++)tiles.add(new Action(stats.get(n),null));rows.add(new Action("stats",null,tiles));}
   }else{
   rows.add(new Action("stat:Общение и объединения",null));
   if(!self){var social=new ArrayList<Action>();if(online&&actions.contains(new JsonPrimitive("tell")))social.add(new Action("Написать",()->openChat(this,Json.str(player,"name"))));social.add(new Action("Пригласить",()->CommunityScreen.invite(this,player)));if(social.size()==2)pair(social.get(0),social.get(1));else rows.addAll(social);}
   rows.add(new Action("Объединения игрока",()->CommunityScreen.groupsFor(this,Json.str(player,"uuid"))));
   if(!self)rows.add(new Action("stat:Безопасность",null));
   if(!self)pair(new Action("Пожаловаться",()->minecraft.setScreen(new ReportScreen(this,"Игрок: "+Json.str(player,"name")+"\n"))),new Action("Общение…",()->PersonalProfileScreen.ignores(this,Json.str(player,"name"))));
   if(!self&&ModerationVoteScreen.enabled())rows.add(new Action("Голосование о нарушении…",()->minecraft.setScreen(new ModerationVoteScreen(this,player))));
   if(ServerMenuClient.admin()||actions.size()>1||resetPermission)rows.add(new Action("Модерация…",()->minecraft.setScreen(new PlayerActionsScreen(this,player,actions,true))));
  }
  }
  var available=new ArrayList<String>();for(var entry:actions){if(!moderationOnly)continue;String action=entry.getAsString();if(action.equals("tell"))continue;if(online||Set.of("ban","pardon").contains(action))available.add(action);}
  int reasonRow=-1,minutesRow=-1,banRow=-1;if(!available.isEmpty()){reasonRow=rows.size();rows.add(new Action("reason",null));if(available.contains("vmute")){minutesRow=rows.size();rows.add(new Action("minutes",null));}if(available.contains("ban")){banRow=rows.size();rows.add(new Action("banMinutes",null));}for(int n=0;n<available.size();n+=2){var pair=new ArrayList<Action>();for(int k=n;k<Math.min(n+2,available.size());k++){String action=available.get(k);pair.add(new Action(action.equals("warn")?"Предупредить":Client.tr("server.command."+action).getString(),()->run(action)));}rows.add(new Action("actions",null,pair));}}
  int panelHeight=Math.min(height-16,(moderationOnly?rows.size()*28+88:Math.max(240,rows.size()*28+120)));panelTop=(height-panelHeight)/2;panelBottom=panelTop+panelHeight;
  int w=Math.min(340,width-40),x=(width-w)/2;scrollArea(rows.size(),panelTop+(moderationOnly?40:70),panelBottom-40,28,x+w+4);
  for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++){int y=panelTop+(moderationOnly?40:70)+(i-firstRow)*28;if(i==reasonRow){var edit=addRenderableWidget(new UiEditBox(font,x,y,w,20,Client.tr("server.reason")));edit.setMaxLength(300);edit.setHint(Client.tr("server.reason"));edit.setValue(reason);edit.setResponder(v->reason=v);}else if(i==banRow){addRenderableWidget(Button.builder(Component.literal("Срок бана: "+(banMinutes.equals("0")?"бессрочно":banMinutes+" мин")+" ▾"),b->minecraft.setScreen(new ChoicePopup(this,"Срок бана",List.of("Бессрочно","30 минут","1 час","1 день","7 дней"),n->{banMinutes=List.of("0","30","60","1440","10080").get(n);rebuildWidgets();},b))).bounds(x,y,w,20).build());}else if(i==minutesRow){var edit=addRenderableWidget(new UiEditBox(font,x,y,70,20,Client.tr("server.muteMinutes")));edit.setMaxLength(5);edit.setFilter(v->v.matches("[0-9]*"));edit.setValue(minutes);edit.setResponder(v->minutes=v);}else if(!rows.get(i).label.startsWith("stat:")&&!rows.get(i).label.equals("stats")&&!rows.get(i).label.equals("bio")){var action=rows.get(i);var buttons=action.pair.isEmpty()?List.of(action):action.pair;int bw=buttons.size()>1?(w-6*(buttons.size()-1))/buttons.size():Math.min(w,Math.max(100,font.width(buttons.get(0).label)+24));for(int n=0;n<buttons.size();n++){var button=buttons.get(n);addRenderableWidget(Button.builder(Component.literal(button.label),b->button.run.run()).bounds(x+n*(bw+6),y,bw,20).build());}}}
  if(!moderationOnly){addRenderableWidget(new UiTab("Профиль",x,panelTop+42,(w-6)/2,!profileActions,()->{profileActions=false;resetScroll();rebuildWidgets();}));addRenderableWidget(new UiTab("Действия",x+(w+6)/2,panelTop+42,(w-6)/2,profileActions,()->{profileActions=true;resetScroll();rebuildWidgets();}));}
  addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds((width+w)/2-80,panelBottom-26,80,20).build());
 }
 static void openChat(Screen parent,String name){
  var mc=net.minecraft.client.Minecraft.getInstance();
  if(mc.player==null||mc.getConnection()==null){mc.setScreen(new TextScreen(parent,Component.literal("Личное сообщение"),"Личные сообщения доступны после подключения к серверу."));return;}
  mc.setScreen(new ChatScreen("/tell "+name+" "));
 }
 private void run(String action){var j=new JsonObject();j.addProperty("action","moderate");j.addProperty("operation",action);j.addProperty("target",Json.str(player,"uuid"));j.addProperty("reason",reason);
  if((action.equals("kick")||action.equals("ban")||action.equals("warn"))&&reason.isBlank()){status=Client.tr("server.reason").getString();return;}
  if(action.equals("vmute"))try{int duration=Integer.parseInt(minutes);if(duration<1||duration>10080)throw new NumberFormatException();j.addProperty("minutes",duration);}catch(NumberFormatException ex){status=Client.tr("server.invalidnumber").getString();return;}
  if(action.equals("ban"))j.addProperty("minutes",Integer.parseInt(banMinutes));
  Component summary=title.copy().append("\n").append(reason);if(action.equals("ban"))summary=summary.copy().append(" · "+(banMinutes.equals("0")?"бессрочно":banMinutes+" мин"));if(action.equals("vmute"))summary=summary.copy().append(" · "+minutes+" min");
  minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes){ServerMenuClient.result="";ServerMenuClient.request(j);}},action.equals("warn")?Component.literal("Предупредить"):Client.tr("server.command."+action),summary));
 }
 @Override public void tick(){if(!ServerMenuClient.available()){minecraft.setScreen(null);return;}boolean reset=ServerMenuClient.state.has("authReset")&&ServerMenuClient.state.get("authReset").getAsBoolean();if(reset!=resetPermission||ServerMenuClient.state.has("actions")&&!actions.equals(ServerMenuClient.state.getAsJsonArray("actions")))rebuildWidgets();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){g.fill(0,0,width,height,UiPalette.color(0xBB090E14));int w=Math.min(360,width-24),left=(width-w)/2;UiTheme.panel(g,left,panelTop,w,panelBottom-panelTop,AccessibilityScreen.background(UiPalette.color(0xFF1B252E)));}
 @Override public void render(GuiGraphics g,int x,int y,float d){
  ModalLayer.render(parent,this,g,d,()->{super.render(g,x,y,d);
   var heading=net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(title,Math.min(340,width-40)-36));
   int headerX=(width-Math.min(340,width-40))/2;PlayerFaceRenderer.draw(g,SkinClient.skin(UUID.fromString(Json.str(player,"uuid"))),headerX,panelTop+8,26);
   Ui.text(g,font,heading,headerX+36,panelTop+10,UiPalette.color(0xE2BE75));
   Ui.text(g,font,Component.literal(PlayerStatisticsText.activity(player)),headerX+36,panelTop+24,player.has("online")&&player.get("online").getAsBoolean()?UiPalette.color(0x79CBA6):UiKit.muted());
   for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++)if(rows.get(i).label.equals("minutes"))Ui.text(g,font,Client.tr("server.muteMinutes"),(width-Math.min(340,width-40))/2+78,panelTop+(moderationOnly?46:76)+(i-firstRow)*28,AccessibilityScreen.foreground(UiPalette.color(0xEEEEEE)));
   for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++)if(rows.get(i).label.startsWith("stat:"))Ui.text(g,font,font.plainSubstrByWidth(rows.get(i).label.substring(5),Math.min(340,width-40)),(width-Math.min(340,width-40))/2,panelTop+(moderationOnly?46:76)+(i-firstRow)*28,AccessibilityScreen.foreground(UiPalette.color(0xBAC7D2)));
   for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++)if(rows.get(i).label.equals("bio")){int yy=panelTop+74+(i-firstRow)*28;for(int n=0;n<rows.get(i).pair.size();n++)Ui.text(g,font,rows.get(i).pair.get(n).label,(width-Math.min(340,width-40))/2,yy+n*12,UiPalette.color(0xBAC7D2),false);}
   int statX=(width-Math.min(340,width-40))/2,statW=Math.min(340,width-40);for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++)if(rows.get(i).label.equals("stats")){int tileY=panelTop+(moderationOnly?40:70)+(i-firstRow)*28;var tiles=rows.get(i).pair;int tw=(statW-6)/2;for(int n=0;n<tiles.size();n++){int tx=statX+n*(tw+6);String[] parts=tiles.get(n).label.split(": ",2);g.fill(tx,tileY,tx+tw,tileY+26,UiPalette.color(0xFF132630));Ui.text(g,font,font.plainSubstrByWidth(parts[0],tw-10),tx+5,tileY+3,UiPalette.color(0x8FA6B5),false);if(parts.length>1)Ui.text(g,font,font.plainSubstrByWidth(parts[1],tw-10),tx+5,tileY+13,UiPalette.color(0xD8E9F2),false);}}
   Ui.status(g,font,status.isEmpty()?ServerMenuClient.result:status,(width-Math.min(340,width-40))/2,panelBottom-39,Math.min(340,width-40),panelBottom-28);
   g.flush();
  });
 }
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){if(parent instanceof FeatureListScreen screen)screen.invalidate();minecraft.setScreen(parent);}
}
