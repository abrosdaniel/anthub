package dev.abros.anthub.client;

import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;

/** Paged server lists; response routing stays bound to the requesting screen. */
final class FeatureListScreen extends ScrollScreen {
    private final java.util.Map<Integer,String> cursors=new java.util.HashMap<>();private JsonObject selectedPlayer;private long dirtyAt,lastLoad;private boolean busy,more;private int loadedPage,refreshThrough;private long nextAt;private final java.util.NavigableMap<Integer,JsonArray> batches=new java.util.TreeMap<>();
    void leavePage(){if(busy){busy=false;dirtyAt=System.currentTimeMillis();}}
    boolean navigationPage(){return kind.equals("players")&&selection==null;}
    void invalidate(){if(dirtyAt==0)dirtyAt=System.currentTimeMillis();}
    private boolean splitPlayers(){return kind.equals("players")&&selection==null&&height>=320&&width-nav.left()>=460;}
    private int listWidth(){return width-nav.left()-20-(splitPlayers()?220:0);}
    private String requestId="";private String focusReport="";private final MenuSidebar nav=new MenuSidebar(this);private java.util.function.Consumer<JsonObject> selection;private String query="";private long searchAt;private boolean allPlayers;private final Screen parent;final String kind;private JsonArray entries=new JsonArray(),actions=new JsonArray();private int page;private String status="";
    FeatureListScreen(Screen parent,String kind){super(Client.tr("server."+kind));this.parent=parent;this.kind=kind;}
    static void open(Screen parent,String kind){var screen=new FeatureListScreen(parent,kind);net.minecraft.client.Minecraft.getInstance().setScreen(screen);screen.load(0);}
    static void searchPlayer(Screen parent,String name){var screen=new FeatureListScreen(parent,"players");screen.query=name;screen.allPlayers=true;net.minecraft.client.Minecraft.getInstance().setScreen(screen);screen.load(0);}
    static void openReport(Screen parent,String id){var screen=new FeatureListScreen(parent,"myReports");screen.focusReport=id;net.minecraft.client.Minecraft.getInstance().setScreen(screen);var request=new JsonObject();request.addProperty("action","myReport");request.addProperty("id",id);screen.requestId=java.util.UUID.randomUUID().toString();request.addProperty("request",screen.requestId);ServerMenuClient.request(request);}
    static void pick(Screen parent,java.util.function.Consumer<JsonObject> selection){var screen=new FeatureListScreen(parent,"players");screen.selection=selection;screen.allPlayers=true;net.minecraft.client.Minecraft.getInstance().setScreen(screen);screen.load(0);}
    private void load(int value){if(busy)return;busy=true;lastLoad=System.currentTimeMillis();page=Math.max(0,Math.min(kind.equals("history")?100:1000,value));status=Client.tr("server.loading").getString();var j=new JsonObject();j.addProperty("action",kind.equals("links")?"menuData":kind);j.addProperty("page",page);j.addProperty("cursor",page==0?"":cursors.getOrDefault(page,""));j.addProperty("query",query);j.addProperty("all",allPlayers);requestId=java.util.UUID.randomUUID().toString();j.addProperty("request",requestId);ServerMenuClient.request(j);}
    void receive(JsonObject j){
        if(!requestId.equals(Json.opt(j,"request","")))return;
        if(j.has("page")&&j.get("page").getAsInt()!=page)return;
        var previousEntries=entries;var previousActions=actions;busy=false;String selectedId=selectedPlayer==null?"":Json.str(selectedPlayer,"uuid");entries=j.has("menu")?j.getAsJsonObject("menu").getAsJsonArray(kind):j.getAsJsonArray(kind.equals("history")?"entries":kind.equals("players")?"players":"reports");

        String next=Json.opt(j,"nextCursor","");cursors.put(page+1,next);boolean cursorPaging=j.has("nextCursor");batches.put(page,entries.deepCopy());loadedPage=Math.max(loadedPage,page);int size=kind.equals("players")?20:kind.equals("history")?10:5;if(cursorPaging?next.isEmpty():entries.size()<size){batches.tailMap(page,false).clear();loadedPage=page;refreshThrough=Math.min(refreshThrough,page);}if(page==loadedPage)more=cursorPaging?!next.isEmpty():entries.size()==size;nextAt=page<refreshThrough?System.currentTimeMillis()+600:0;entries=new JsonArray();var ids=new java.util.HashSet<String>();for(var batch:batches.values())for(var entry:batch){var item=entry.getAsJsonObject();String id=Json.opt(item,"uuid",Json.opt(item,"id",entry.toString()));if(ids.add(id))entries.add(entry);}
        if(selectedId.isEmpty()&&kind.equals("players")&&selection==null&&!entries.isEmpty())selectedPlayer=entries.get(0).getAsJsonObject();if(!selectedId.isEmpty()){selectedPlayer=null;for(var entry:entries)if(Json.opt(entry.getAsJsonObject(),"uuid","").equals(selectedId))selectedPlayer=entry.getAsJsonObject();}
        if(!focusReport.isEmpty()&&entries.size()==1){focusReport="";minecraft.setScreen(new ReportDetailScreen(parent,entries.get(0).getAsJsonObject(),false));return;}
        if(j.has("actions"))actions=j.getAsJsonArray("actions");status=entries.isEmpty()?Client.tr("content.empty").getString():"";if(!previousEntries.equals(entries)||!previousActions.equals(actions))rebuildWidgets();
    }
    void failure(String text){busy=false;nextAt=0;status=text;}
    private void reset(){if(busy)return;searchAt=0;batches.clear();cursors.clear();loadedPage=refreshThrough=0;nextAt=0;resetScroll();load(0);}
    private void refresh(){if(busy)return;refreshThrough=loadedPage;load(0);}
    private void next(){if(!busy&&more&&nextAt==0&&System.currentTimeMillis()-lastLoad>600)load(loadedPage+1);}
    @Override protected void init(){int w=kind.equals("players")?listWidth():Math.min(620,width-40),left=kind.equals("players")?nav.left():(width-w)/2;if(kind.equals("players"))nav.build("players",this::addRenderableWidget);boolean reportList=kind.equals("reports")||kind.equals("myReports");int stride=reportList?(AccessibilityScreen.compact()?60:76):28;int top=kind.equals("players")?70:40;scrollArea(entries.size(),top,height-70,stride,left+w+4);
        if(kind.equals("players")){var search=addRenderableWidget(new EditBox(font,left,38,Math.max(20,w-140),20,Component.literal("Поиск по нику")));search.setHint(Component.literal("Ник игрока"));search.setValue(query);search.setMaxLength(32);search.setResponder(v->{query=v;searchAt=System.currentTimeMillis()+500;});addRenderableWidget(Button.builder(Component.literal("Найти"),b->reset()).bounds(left+w-136,38,48,20).build());addRenderableWidget(Button.builder(Component.literal((allPlayers?"Все":"В сети")+" ▾"),b->{if(!busy)minecraft.setScreen(new ChoicePopup(this,"Игроки",java.util.List.of("В сети","Все игроки"),n->{allPlayers=n==1;reset();},b).current(allPlayers?1:0));}).bounds(left+w-84,38,60,20).build());addRenderableWidget(Button.builder(Component.literal("×"),b->{query="";allPlayers=false;reset();rebuildWidgets();}).bounds(left+w-20,38,20,20).tooltip(Tooltip.create(Component.literal("Сбросить поиск и показать игроков в сети"))).build()).active=!query.isBlank()||allPlayers;}
        for(int i=firstRow;i<Math.min(entries.size(),firstRow+visibleRows);i++){var item=entries.get(i).getAsJsonObject();String label=label(item);int y=top+(i-firstRow)*stride;
            if(kind.equals("players"))addRenderableWidget(new PlayerRow(left+24,y,w-24,item,()->detail(item),selectedPlayer!=null&&Json.str(selectedPlayer,"uuid").equals(Json.str(item,"uuid"))));
            else if(reportList){var card=item.deepCopy();card.addProperty("section","moderation");card.addProperty("title","Обращение: "+Json.opt(item,"player","Игрок"));card.addProperty("attention",Client.tr("server.status."+Json.opt(item,"status","open")).getString()+(Json.opt(item,"priority","normal").equals("high")?" · Высокий приоритет":""));card.addProperty("preview",Json.opt(item,"message",""));card.addProperty("footer",Json.opt(item,"assignedName","").isBlank()?"Ответственный не назначен":"Ответственный: "+Json.opt(item,"assignedName",""));addRenderableWidget(new CommunityCard(left,y,w,stride-6,card,()->detail(item)));}
            else addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label,w-12)),b->detail(item)).bounds(left,y,w,24).build());}

        if(splitPlayers()&&selectedPlayer!=null){
            int x=width-222,wPanel=202;
            int actionY=112+PlayerStatisticsText.summary(selectedPlayer).size()*14;
            int half=(wPanel-20)/2;boolean online=selectedPlayer.has("online")&&selectedPlayer.get("online").getAsBoolean();
            boolean self=Json.str(selectedPlayer,"uuid").equals(Json.opt(ServerMenuClient.state,"uuid",""));
            if(!self){if(online&&actions.contains(new JsonPrimitive("tell")))addRenderableWidget(Button.builder(Component.literal("Написать"),b->PlayerActionsScreen.openChat(this,Json.str(selectedPlayer,"name"))).bounds(x+8,actionY,half,20).build());
            addRenderableWidget(Button.builder(Component.literal("Пожаловаться"),b->minecraft.setScreen(new ReportScreen(this,"Игрок: "+Json.str(selectedPlayer,"name")+"\n"))).bounds(x+12+half,actionY,half,20).build());
            addRenderableWidget(Button.builder(Component.literal("Пригласить в объединение"),b->CommunityScreen.invite(this,selectedPlayer)).bounds(x+8,actionY+24,Math.min(wPanel-16,176),20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Профиль игрока…"),b->minecraft.setScreen(new PlayerActionsScreen(this,selectedPlayer,actions))).bounds(x+8,actionY+(self?0:48),Math.min(wPanel-16,176),20).build());

        }
        addRenderableWidget(Button.builder(Client.tr("server.refresh"),b->refresh()).bounds(kind.equals("players")?nav.left():width/2-50,height-28,80,20).build());
        addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width-114,height-28,100,20).build());
    }
    private String label(JsonObject j){return switch(kind){case "players" -> Json.str(j,"name")+(j.has("online")&&j.get("online").getAsBoolean()?" · ●":"");case "links" -> Json.str(j,"name");case "history" -> Json.str(j,"actor")+" · "+Json.str(j,"action");default -> Client.tr("server.status."+Json.opt(j,"status","open")).getString()+" · "+Json.str(j,"player")+" · "+Json.str(j,"message");};}
    static String localTime(String time){return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm z").format(Instant.parse(time).atZone(AccessibilityScreen.zone()));}
    private void detail(JsonObject j){
        if(kind.equals("players")){if(selection!=null){minecraft.setScreen(parent);selection.accept(j);}else if(splitPlayers()){selectedPlayer=j;rebuildWidgets();}else minecraft.setScreen(new PlayerActionsScreen(this,j,actions));return;}
        if(kind.equals("links")){handleComponentClicked(Component.literal(Json.str(j,"name")).withStyle(s->s.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,Json.str(j,"url")))).getStyle());return;}
        if(kind.equals("history")){minecraft.setScreen(new TextScreen(this,title,Instant.ofEpochMilli(j.get("at").getAsLong()).toString()+"\n"+label(j)+"\n"+Json.str(j,"detail")));return;}
        minecraft.setScreen(new ReportDetailScreen(this,j,kind.equals("reports")));
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if((key==257||key==335)&&kind.equals("players")&&getFocused() instanceof EditBox){reset();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override protected void onScrollEnd(){next();}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(kind.equals("players")&&nav.scroll(x,dy)){rebuildWidgets();return true;}boolean used=super.mouseScrolled(x,y,dx,dy);if(dy<0&&firstRow+visibleRows>=entries.size())next();return used;}
    @Override public void tick(){if(!ServerMenuClient.available())minecraft.setScreen(null);else if(busy&&System.currentTimeMillis()-lastLoad>15000)failure("Нет ответа. Нажмите «Обновить».");else if(!busy&&searchAt>0&&System.currentTimeMillis()>=searchAt&&System.currentTimeMillis()-lastLoad>=600){reset();}else if(!busy&&nextAt>0&&System.currentTimeMillis()>=nextAt){nextAt=0;load(page+1);}else if(!busy&&dirtyAt>0&&System.currentTimeMillis()-lastLoad>750){dirtyAt=0;refresh();}else if((kind.equals("reports")&&!ServerMenuClient.may("anthub.reports")||kind.equals("history")&&!ServerMenuClient.admin()))minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){super.renderBackground(g,mx,my,d);if(splitPlayers()){
        int x=width-222;g.fill(x,38,width-20,Math.min(height-42,selectedPlayer==null?104:144+PlayerStatisticsText.summary(selectedPlayer).size()*14+(Json.str(selectedPlayer,"uuid").equals(Json.opt(ServerMenuClient.state,"uuid",""))?0:48)),AccessibilityScreen.background(UiPalette.color(0xCE1B252E)));g.fill(x,38,width-20,41,AccessibilityScreen.background(UiPalette.color(0xFF82B6F2)));
        if(selectedPlayer==null){Ui.status(g,font,"Выберите игрока слева, чтобы открыть его карточку.",x+12,62,178,height-78);return;}
        var info=minecraft.getConnection()==null?null:minecraft.getConnection().getPlayerInfo(java.util.UUID.fromString(Json.str(selectedPlayer,"uuid")));
        PlayerFaceRenderer.draw(g,SkinClient.skin(java.util.UUID.fromString(Json.str(selectedPlayer,"uuid"))),x+12,54,32);
        g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(PlayerText.name(selectedPlayer),140)),x+52,58,0xFFFFFF);
        g.drawString(font,selectedPlayer.has("online")&&selectedPlayer.get("online").getAsBoolean()?"В сети":"Не в сети",x+52,74,selectedPlayer.has("online")&&selectedPlayer.get("online").getAsBoolean()?UiPalette.color(0x79CBA6):UiPalette.color(0xEF7777));
        if(info!=null)g.drawString(font,"Пинг: "+info.getLatency()+" мс",x+12,90,AccessibilityScreen.foreground(UiPalette.color(0xBAC7D2)));
        int statY=104;for(String line:PlayerStatisticsText.summary(selectedPlayer)){g.drawString(font,font.plainSubstrByWidth(line,178),x+12,statY,AccessibilityScreen.foreground(UiPalette.color(0xBAC7D2)),false);statY+=14;}
    }}
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);if(kind.equals("players")&&minecraft.getConnection()!=null)for(int i=firstRow;i<Math.min(entries.size(),firstRow+visibleRows);i++){var player=entries.get(i).getAsJsonObject();var info=minecraft.getConnection().getPlayerInfo(java.util.UUID.fromString(Json.str(player,"uuid")));net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,SkinClient.skin(java.util.UUID.fromString(Json.str(player,"uuid"))),nav.left(),72+(i-firstRow)*28,20);}UiHeading.page(g,font,title,width);if(!status.isEmpty())g.drawCenteredString(font,status,width/2,height-66,AccessibilityScreen.foreground(UiPalette.color(0xEEEEEE)));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
