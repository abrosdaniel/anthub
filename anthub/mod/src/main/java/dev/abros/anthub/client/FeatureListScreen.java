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
    private JsonObject selectedPlayer;
    private boolean splitPlayers(){return kind.equals("players")&&selection==null&&height>=290&&width-nav.left()>=460;}
    private int listWidth(){return width-nav.left()-20-(splitPlayers()?220:0);}
    private String focusReport="";private final MenuSidebar nav=new MenuSidebar(this);private java.util.function.Consumer<JsonObject> selection;private String query="";private boolean allPlayers;private final Screen parent;final String kind;private JsonArray entries=new JsonArray(),actions=new JsonArray();private int page;private String status="";
    FeatureListScreen(Screen parent,String kind){super(Client.tr("server."+kind));this.parent=parent;this.kind=kind;}
    static void open(Screen parent,String kind){var screen=new FeatureListScreen(parent,kind);net.minecraft.client.Minecraft.getInstance().setScreen(screen);screen.load(0);}
    static void openReport(Screen parent,String id){var screen=new FeatureListScreen(parent,"myReports");screen.focusReport=id;net.minecraft.client.Minecraft.getInstance().setScreen(screen);var request=new JsonObject();request.addProperty("action","myReport");request.addProperty("id",id);ServerMenuClient.request(request);}
    static void pick(Screen parent,java.util.function.Consumer<JsonObject> selection){var screen=new FeatureListScreen(parent,"players");screen.selection=selection;net.minecraft.client.Minecraft.getInstance().setScreen(screen);screen.load(0);}
    private void load(int value){page=Math.max(0,Math.min(kind.equals("history")?100:1000,value));status=Client.tr("server.loading").getString();var j=new JsonObject();j.addProperty("action",kind.equals("links")?"menuData":kind);j.addProperty("page",page);j.addProperty("query",query);j.addProperty("all",allPlayers);ServerMenuClient.request(j);}
    void receive(JsonObject j){
        if(j.has("page")&&j.get("page").getAsInt()!=page)return;
        selectedPlayer=null;entries=j.has("menu")?j.getAsJsonObject("menu").getAsJsonArray(kind):j.getAsJsonArray(kind.equals("history")?"entries":kind.equals("players")?"players":"reports");

        if(!focusReport.isEmpty()&&entries.size()==1){focusReport="";minecraft.setScreen(new ReportDetailScreen(parent,entries.get(0).getAsJsonObject(),false));return;}
        if(j.has("actions"))actions=j.getAsJsonArray("actions");status=entries.isEmpty()?Client.tr("content.empty").getString():"";rebuildWidgets();
    }
    void failure(String text){status=text;}
    @Override protected void init(){int w=kind.equals("players")?listWidth():Math.min(620,width-40),left=kind.equals("players")?nav.left():(width-w)/2;if(kind.equals("players"))nav.build("players",this::addRenderableWidget);int top=kind.equals("players")?70:40;scrollArea(entries.size(),top,height-70,28,left+w+4);
        if(kind.equals("players")){var search=addRenderableWidget(new EditBox(font,left,38,Math.max(40,w-108),20,Component.literal("Поиск по нику")));search.setValue(query);search.setMaxLength(32);search.setResponder(v->query=v);addRenderableWidget(Button.builder(Component.literal("Найти"),b->load(0)).bounds(left+w-104,38,48,20).build());addRenderableWidget(Button.builder(Component.literal(allPlayers?"Все":"В сети"),b->{allPlayers=!allPlayers;load(0);}).bounds(left+w-52,38,52,20).build());}
        for(int i=firstRow;i<Math.min(entries.size(),firstRow+visibleRows);i++){var item=entries.get(i).getAsJsonObject();String label=label(item);int y=top+(i-firstRow)*28;
            if(kind.equals("players"))addRenderableWidget(new PlayerRow(left+24,y,w-24,item,()->detail(item),selectedPlayer!=null&&Json.str(selectedPlayer,"uuid").equals(Json.str(item,"uuid"))));
            else addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label,w-12)),b->detail(item)).bounds(left,y,w,24).build());}

        if(splitPlayers()&&selectedPlayer!=null){
            int x=width-222,wPanel=202;
            addRenderableWidget(Button.builder(Component.literal("Действия с игроком"),b->minecraft.setScreen(new PlayerActionsScreen(this,selectedPlayer,actions))).bounds(x+8,142,wPanel-16,20).build());
            addRenderableWidget(Button.builder(Component.literal("Объединения игрока"),b->CommunityScreen.groupsFor(this,Json.str(selectedPlayer,"uuid"))).bounds(x+8,166,wPanel-16,20).build());
        }
        if(!kind.equals("links")){
            var prev=addRenderableWidget(Button.builder(Client.tr("server.previous"),b->load(page-1)).bounds(left,height-54,100,20).build());prev.active=page>0;
            var next=addRenderableWidget(Button.builder(Client.tr("server.next"),b->load(page+1)).bounds(left+104,height-54,100,20).build());next.active=entries.size()>=(kind.equals("players")?20:kind.equals("history")?10:5);
        }
        addRenderableWidget(Button.builder(Client.tr("server.refresh"),b->load(page)).bounds(kind.equals("players")?nav.left():width/2-50,height-28,80,20).build());
        addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width-114,height-28,100,20).build());
    }
    private String label(JsonObject j){return switch(kind){case "players" -> Json.str(j,"name")+" · "+Json.opt(j,"role","")+(j.has("online")&&j.get("online").getAsBoolean()?" · ●":"");case "links" -> Json.str(j,"name");case "history" -> Json.str(j,"actor")+" · "+Json.str(j,"action");default -> Client.tr("server.status."+Json.opt(j,"status","open")).getString()+" · "+Json.str(j,"player")+" · "+Json.str(j,"message");};}
    static String localTime(String time){return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm z").format(Instant.parse(time).atZone(ZoneId.systemDefault()));}
    private void detail(JsonObject j){
        if(kind.equals("players")){if(selection!=null){minecraft.setScreen(parent);selection.accept(j);}else if(splitPlayers()){selectedPlayer=j;rebuildWidgets();}else minecraft.setScreen(new PlayerActionsScreen(this,j,actions));return;}
        if(kind.equals("links")){handleComponentClicked(Component.literal(Json.str(j,"name")).withStyle(s->s.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,Json.str(j,"url")))).getStyle());return;}
        if(kind.equals("history")){minecraft.setScreen(new TextScreen(this,title,Instant.ofEpochMilli(j.get("at").getAsLong()).toString()+"\n"+label(j)+"\n"+Json.str(j,"detail")));return;}
        minecraft.setScreen(new ReportDetailScreen(this,j,kind.equals("reports")));
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(kind.equals("players")&&nav.scroll(x,dy)){rebuildWidgets();return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public void tick(){if(!ServerMenuClient.available())minecraft.setScreen(null);else if((kind.equals("reports")||kind.equals("history"))&&!ServerMenuClient.admin())minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){super.renderBackground(g,mx,my,d);if(splitPlayers()){
        int x=width-222;g.fill(x,38,width-20,height-70,0xCE1B252E);g.fill(x,38,width-20,41,0xFF82B6F2);
        if(selectedPlayer==null){Ui.status(g,font,"Выберите игрока слева, чтобы открыть его карточку.",x+12,62,178,height-78);return;}
        var info=minecraft.getConnection()==null?null:minecraft.getConnection().getPlayerInfo(java.util.UUID.fromString(Json.str(selectedPlayer,"uuid")));
        if(info!=null)PlayerFaceRenderer.draw(g,info.getSkin(),x+12,54,32);
        g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(PlayerText.name(selectedPlayer),140)),x+52,58,0xFFFFFF);
        g.drawString(font,selectedPlayer.has("online")&&selectedPlayer.get("online").getAsBoolean()?"В сети":"Не в сети",x+52,74,0x79CBA6);
        g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(PlayerText.text("Роль: "+Json.opt(selectedPlayer,"role","Игрок")),178)),x+12,104,0xBAC7D2);
    }}
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);if(kind.equals("players")&&minecraft.getConnection()!=null)for(int i=firstRow;i<Math.min(entries.size(),firstRow+visibleRows);i++){var player=entries.get(i).getAsJsonObject();var info=minecraft.getConnection().getPlayerInfo(java.util.UUID.fromString(Json.str(player,"uuid")));if(info!=null)net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,info.getSkin(),nav.left(),72+(i-firstRow)*28,20);}g.drawCenteredString(font,title,width/2,16,0xE2BE75);if(!status.isEmpty())g.drawCenteredString(font,status,width/2,height-66,0xEEEEEE);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
