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
    private final dev.abros.anthub.core.PagedMenuController controller;
    private JsonObject selectedPlayer;
    void leavePage(){controller.leave();ServerMenuClient.cancelReads(this);}
    boolean navigationPage(){return kind.equals("players")&&selection==null;}
    void invalidate(){controller.invalidate();}
    private boolean splitPlayers(){return kind.equals("players")&&selection==null&&height>=320&&UiWorkspace.fit(width,height).page().width()>=460;}
    private int playerRowsTop(){return listWidth()<220?96:70;}
    private int playerPanelLeft(){return nav.left()+listWidth()+18;}
    private int listWidth(){return UiWorkspace.fit(width,height).page().width()-(splitPlayers()?220:0);}
    private String focusReport="";private final MenuSidebar nav=new MenuSidebar(this);private java.util.function.Consumer<JsonObject> selection;private String query="";private long searchAt;private boolean allPlayers;private final Screen parent;final String kind;private JsonArray entries=new JsonArray(),actions=new JsonArray();private String status="";
    FeatureListScreen(Screen parent,String kind){super(Client.tr("server."+kind));this.parent=parent;this.kind=kind;this.controller=new dev.abros.anthub.core.PagedMenuController(kind,ServerMenuClient::request,System::currentTimeMillis);}
    static void open(Screen parent,String kind){if(kind.equals("reports")&&ServerMenuClient.supports("community-extensions")){net.minecraft.client.Minecraft.getInstance().setScreen(new ReportQueueScreen(parent,"new"));return;}var screen=new FeatureListScreen(parent,kind);net.minecraft.client.Minecraft.getInstance().setScreen(screen);screen.load(0);}
    static void searchPlayer(Screen parent,String name){var screen=new FeatureListScreen(parent,"players");screen.query=name;screen.allPlayers=true;net.minecraft.client.Minecraft.getInstance().setScreen(screen);screen.load(0);}
    static void openReport(Screen parent,String id){var screen=new FeatureListScreen(parent,"myReports");screen.focusReport=id;net.minecraft.client.Minecraft.getInstance().setScreen(screen);var request=new JsonObject();request.addProperty("action","myReport");request.addProperty("id",id);screen.controller.request(request,0);}
    static void pick(Screen parent,java.util.function.Consumer<JsonObject> selection){var screen=new FeatureListScreen(parent,"players");screen.selection=selection;screen.allPlayers=true;net.minecraft.client.Minecraft.getInstance().setScreen(screen);screen.load(0);}
    private void load(int page){var packet=new JsonObject();packet.addProperty("action",kind.equals("links")?"menuData":kind);packet.addProperty("query",query);packet.addProperty("all",allPlayers);if(controller.request(packet,page))status=Client.tr("server.loading").getString();}
    void receive(JsonObject reply){
        var result=controller.receive(reply);if(!result.accepted())return;
        if(result.failed()){status=controller.error();rebuildWidgets();return;}
        String selectedId=selectedPlayer==null?"":Json.str(selectedPlayer,"uuid");entries=controller.entries();
        if(selectedId.isEmpty()&&kind.equals("players")&&selection==null&&!entries.isEmpty())selectedPlayer=entries.get(0).getAsJsonObject();
        if(!selectedId.isEmpty()){selectedPlayer=null;for(var entry:entries)if(Json.opt(entry.getAsJsonObject(),"uuid","").equals(selectedId))selectedPlayer=entry.getAsJsonObject();}
        if(!focusReport.isEmpty()&&entries.size()==1){focusReport="";minecraft.setScreen(new ReportDetailScreen(parent,entries.get(0).getAsJsonObject(),false));return;}
        var metadata=controller.reply();if(metadata.has("actions"))actions=metadata.getAsJsonArray("actions");
        status=entries.isEmpty()?Client.tr("content.empty").getString():"";if(result.changed())rebuildWidgets();
    }
    void failure(String text){controller.failure(text);status=text;rebuildWidgets();}
    private void reset(){if(controller.busy())return;searchAt=0;controller.reset();entries=controller.entries();resetScroll();load(0);rebuildWidgets();}
    private void refresh(){if(controller.busy())return;controller.refresh();load(0);}
    private void next(){if(controller.nextAllowed())load(controller.loadedPage()+1);}
    @Override protected void init(){int w=kind.equals("players")?listWidth():Math.min(620,UiPage.body(width,height).width()),left=kind.equals("players")?nav.left():(width-w)/2;if(kind.equals("players"))nav.build("players",this::addRenderableWidget);boolean reportList=kind.equals("reports")||kind.equals("myReports");int stride=reportList?(AccessibilityScreen.compact()?60:76):28;int top=kind.equals("players")?playerRowsTop():40;scrollArea(entries.size(),new dev.abros.anthub.core.NativeLayout.Box(left,top,Math.max(0,w),Math.max(0,(height-70)-(top))),stride);
        if(kind.equals("players")){boolean stacked=w<220;int filterY=stacked?64:38;var search=UiSearchToolbar.query(font,new dev.abros.anthub.core.NativeLayout.Box(left,38,stacked?w:w-88,20),"Ник игрока",query,this::addRenderableWidget,v->{query=v;searchAt=System.currentTimeMillis()+500;},this::reset,!controller.busy());search.setMaxLength(32);addRenderableWidget(UiActions.button(Component.literal((allPlayers?"Все":"В сети")+" ▾"),UiActions.Tone.NORMAL,"",b->{if(!controller.busy())minecraft.setScreen(new ChoicePopup(this,"Игроки",java.util.List.of("В сети","Все игроки"),n->{allPlayers=n==1;reset();},b).current(allPlayers?1:0));}).bounds(stacked?left:left+w-84,filterY,stacked?w-24:60,20).build());addRenderableWidget(UiActions.button(Component.literal("×"),UiActions.Tone.NORMAL,"",b->{query="";allPlayers=false;reset();rebuildWidgets();}).bounds(left+w-20,filterY,20,20).tooltip(Tooltip.create(Component.literal("Сбросить поиск и показать игроков в сети"))).build()).active=!query.isBlank()||allPlayers;}
        for(int i=firstRow;i<Math.min(entries.size(),firstRow+visibleRows);i++){var item=entries.get(i).getAsJsonObject();String label=label(item);int y=top+(i-firstRow)*stride;
            if(kind.equals("players"))addRenderableWidget(new PlayerRow(left+24,y,w-24,item,()->detail(item),selectedPlayer!=null&&Json.str(selectedPlayer,"uuid").equals(Json.str(item,"uuid"))));
            else if(reportList){var card=item.deepCopy();card.addProperty("section","moderation");card.addProperty("title","Обращение: "+Json.opt(item,"player","Игрок"));card.addProperty("attention",Client.tr("server.status."+Json.opt(item,"status","open")).getString()+(Json.opt(item,"priority","normal").equals("high")?" · Высокий приоритет":""));card.addProperty("preview",Json.opt(item,"message",""));card.addProperty("footer",Json.opt(item,"assignedName","").isBlank()?"Ответственный не назначен":"Ответственный: "+Json.opt(item,"assignedName",""));addRenderableWidget(new CommunityCard(left,y,w,stride-6,card,()->detail(item)));}
            else addRenderableWidget(UiActions.button(Component.literal(font.plainSubstrByWidth(label,w-12)),UiActions.Tone.NORMAL,"",b->detail(item)).bounds(left,y,w,24).build());}

        if(splitPlayers()&&selectedPlayer!=null){
            int x=playerPanelLeft(),wPanel=202;
            int actionY=112+PlayerStatisticsText.summary(selectedPlayer).size()*14;
            int half=(wPanel-20)/2;boolean online=selectedPlayer.has("online")&&selectedPlayer.get("online").getAsBoolean();
            boolean self=Json.str(selectedPlayer,"uuid").equals(Json.opt(ServerMenuClient.state,"uuid",""));
            if(!self){if(online&&actions.contains(new JsonPrimitive("tell")))addRenderableWidget(UiActions.button(Component.literal("Написать"),UiActions.Tone.NORMAL,"",b->PlayerActionsScreen.openChat(this,Json.str(selectedPlayer,"name"))).bounds(x+8,actionY,half,20).build());
            addRenderableWidget(UiActions.button(Component.literal("Пожаловаться"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ReportScreen(this,"Игрок: "+Json.str(selectedPlayer,"name")+"\n"))).bounds(x+12+half,actionY,half,20).build());
            addRenderableWidget(UiActions.button(Component.literal("Пригласить в объединение"),UiActions.Tone.NORMAL,"",b->CommunityScreen.invite(this,selectedPlayer)).bounds(x+8,actionY+24,Math.min(wPanel-16,176),20).build());
            }
            addRenderableWidget(UiActions.button(Component.literal("Профиль игрока…"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new PlayerActionsScreen(this,selectedPlayer,actions))).bounds(x+8,actionY+(self?0:48),Math.min(wPanel-16,176),20).build());

        }
        var footer=kind.equals("players")?UiPageFooter.workspace(width,height):UiPageFooter.fit(new dev.abros.anthub.core.NativeLayout.Box(left,height-32,w,20));
        footer.start(UiActions.Command.REFRESH,this::addRenderableWidget,this::refresh).active=!controller.busy();
        footer.end(UiActions.Command.BACK,this::addRenderableWidget,this::onClose);

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
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(kind.equals("players")&&nav.scroll(x,y,dy)){rebuildWidgets();return true;}boolean used=super.mouseScrolled(x,y,dx,dy);if(used&&dy<0&&firstRow+visibleRows>=entries.size())next();return used;}
    @Override public void tick(){if(!ServerMenuClient.available()){minecraft.setScreen(null);return;}if(controller.timeout()){status=controller.error();rebuildWidgets();}else if(!controller.busy()&&searchAt>0&&System.currentTimeMillis()>=searchAt&&System.currentTimeMillis()-controller.sent()>=600)reset();else if(controller.refreshDue())load(controller.page()+1);else if(controller.dirtyDue())refresh();else if((kind.equals("reports")&&!ServerMenuClient.may("anthub.reports")||kind.equals("history")&&!ServerMenuClient.admin()))minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){super.renderBackground(g,mx,my,d);if(!kind.equals("players"))UiPage.draw(g,width,height);if(splitPlayers()){
        int x=playerPanelLeft();UiKit.material(g,x,38,202,Math.min(height-42,selectedPlayer==null?104:144+PlayerStatisticsText.summary(selectedPlayer).size()*14+(Json.str(selectedPlayer,"uuid").equals(Json.opt(ServerMenuClient.state,"uuid",""))?0:48))-38);g.fill(x,38,x+202,41,AccessibilityScreen.background(UiPalette.color(0xFF82B6F2)));
        if(selectedPlayer==null){Ui.status(g,font,"Выберите игрока слева, чтобы открыть его карточку.",x+12,62,178,height-78);return;}
        var info=minecraft.getConnection()==null?null:minecraft.getConnection().getPlayerInfo(java.util.UUID.fromString(Json.str(selectedPlayer,"uuid")));
        PlayerFaceRenderer.draw(g,SkinClient.skin(java.util.UUID.fromString(Json.str(selectedPlayer,"uuid"))),x+12,54,32);
        Ui.text(g,font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(PlayerText.name(selectedPlayer),140)),x+52,58,UiKit.text(),false);
        Ui.text(g,font,UiKit.fit(font,PlayerStatisticsText.activity(selectedPlayer),140),x+52,74,selectedPlayer.has("online")&&selectedPlayer.get("online").getAsBoolean()?UiPalette.color(0x79CBA6):UiPalette.color(0xEF7777));
        if(info!=null)Ui.text(g,font,"Пинг: "+info.getLatency()+" мс",x+12,90,AccessibilityScreen.foreground(UiPalette.color(0xBAC7D2)));
        int statY=104;for(String line:PlayerStatisticsText.summary(selectedPlayer)){Ui.text(g,font,font.plainSubstrByWidth(line,178),x+12,statY,AccessibilityScreen.foreground(UiPalette.color(0xBAC7D2)),false);statY+=14;}
    }}
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);if(kind.equals("players")&&minecraft.getConnection()!=null)for(int i=firstRow;i<Math.min(entries.size(),firstRow+visibleRows);i++){var player=entries.get(i).getAsJsonObject();var info=minecraft.getConnection().getPlayerInfo(java.util.UUID.fromString(Json.str(player,"uuid")));net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,SkinClient.skin(java.util.UUID.fromString(Json.str(player,"uuid"))),nav.left(),playerRowsTop()+2+(i-firstRow)*28,20);}UiHeading.page(g,font,title,width);nav.drawFrame(g);if(entries.isEmpty())UiState.draw(g,font,controller.failed()?UiState.Kind.ERROR:controller.busy()?UiState.Kind.LOADING:query.isBlank()?UiState.Kind.EMPTY:UiState.Kind.FILTERED,controller.failed()?"Список недоступен":controller.busy()?"Загружаем список…":kind.equals("players")?query.isBlank()?"Нет игроков":"Игроки не найдены":"Пока нет записей",controller.failed()?status:controller.busy()?"Получаем актуальные данные с сервера.":kind.equals("players")?"Измените поиск или выберите «Все игроки».":"Записи появятся здесь после обновления.",kind.equals("players")?nav.left()+8:(width-Math.min(620,UiPage.body(width,height).width()))/2+8,kind.equals("players")?playerRowsTop()+8:48,(kind.equals("players")?listWidth():Math.min(620,UiPage.body(width,height).width()))-16,height-70);if(!status.isEmpty()&&!entries.isEmpty())Ui.centered(g,font,status,width/2,height-66,AccessibilityScreen.foreground(UiPalette.color(0xEEEEEE)));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){controller.leave();ServerMenuClient.cancelReads(this);minecraft.setScreen(parent);}
}
