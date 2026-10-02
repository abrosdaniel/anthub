package dev.abros.anthub.client;

import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Personal inbox overlay. Keeps loaded rows visible while refreshing each page. */
final class NotificationPopup extends ScrollScreen implements CommunityScreen.Receiver {
    private final Screen parent;
    private final dev.abros.anthub.core.PagedMenuController controller=new dev.abros.anthub.core.PagedMenuController("notifications",ServerMenuClient::request,System::currentTimeMillis);
    private JsonArray entries=new JsonArray();
    private String status="",category="";
    private boolean started,preferencesLoaded;
    private JsonObject target;private JsonObject preferences=new JsonObject();

    NotificationPopup(Screen parent) { super(Component.literal("Уведомления")); this.parent = parent; }
    void refreshVote(){rebuildWidgets();}
    private boolean activeVote(){return Json.opt(ServerMenuClient.moderationVote,"status","").equals("open")&&ServerMenuClient.moderationVote.has("endsAt")&&ServerMenuClient.moderationVote.get("endsAt").getAsLong()>System.currentTimeMillis();}
    void invalidate() { controller.invalidate(); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return Math.max(8, (height - 320) / 2); }
    private int bottom() { return Math.min(height - 8, top() + 320); }
    private int panelWidth() { return Math.min(410, width - 24); }

    @Override protected void init() {
        ModalLayer.prepare(parent,this);
        int x = left(), w = panelWidth(), y = top();
        var categories=List.of("","groups","events","polls","board","ideas","help");var labels=List.of("Все уведомления","Объединения","События","Голосования","Объявления","Предложения","Обращения");addRenderableWidget(UiActions.button(Component.literal(labels.get(categories.indexOf(category))+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,"Категория",labels,index->{if(controller.busy())return;category=categories.get(index);controller.reset();entries=controller.entries();resetScroll();send("list","");rebuildWidgets();},b).current(categories.indexOf(category)))).bounds(x+10,y+34,w-28,20).build());
        if(activeVote())addRenderableWidget(UiActions.button(Component.literal(font.plainSubstrByWidth("Голосование о наказании: "+Json.opt(ServerMenuClient.moderationVote,"name",""),w-40)),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ModerationVoteScreen(this,null))).bounds(x+10,y+60,w-28,24).build());
        scrollArea(entries.size(),new dev.abros.anthub.core.NativeLayout.Box(x,y + (activeVote()?96:64),Math.max(0,w-10),Math.max(0,(bottom() - (controller.failed()?86:64))-(y + (activeVote()?96:64)))),40);
        for (int i = firstRow; i < Math.min(entries.size(), firstRow + visibleRows); i++) {
            var notice = entries.get(i).getAsJsonObject();
            addRenderableWidget(new NotificationRow(x+10,y+(activeVote()?96:64)+(i-firstRow)*40,w-28,notice,()->{if(controller.busy())return;target=notice;controller.refresh();send("read",Json.str(notice,"id"));}));
        }
        var footer=UiActions.row(new dev.abros.anthub.core.NativeLayout.Box(x+10,bottom()-52,w-20,20),this::addRenderableWidget,
            UiActions.action("Прочитать всё",()->{if(controller.busy())return;target=null;controller.refresh();send("read","");},!controller.busy()&&java.util.stream.StreamSupport.stream(entries.spliterator(),false).anyMatch(e->!e.getAsJsonObject().get("read").getAsBoolean())),
            UiActions.action("Настройки",()->minecraft.setScreen(new CommunityPreferences(this,preferences)),preferencesLoaded));
        footer.get(1).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(preferencesLoaded?"Какие события присылать и как о них сообщать":"Настройки загружаются с сервера")));
        if(controller.failed()){var retry=UiActions.command(UiActions.Command.RETRY,new dev.abros.anthub.core.NativeLayout.Box(x+10,bottom()-76,w-20,20),this::addRenderableWidget,()->{if(controller.busy())return;controller.retry();rebuildWidgets();});retry.active=!controller.busy();}
        UiActions.close(new dev.abros.anthub.core.NativeLayout.Box(x+10,bottom()-28,w-20,20),this::addRenderableWidget,this::onClose);

        if (!started) { started = true; send("list", ""); }
    }

    private void send(String op,String id){send(op,id,controller.page());}
    private void send(String op,String id,int page){
        var packet=new JsonObject();packet.addProperty("category",category);packet.addProperty("action","community");
        packet.addProperty("section","notifications");packet.addProperty("op",op);packet.addProperty("id",id);
        controller.request(packet,page);
    }
    @Override public void receiveCommunity(JsonObject reply){
        boolean hadPreferences=preferencesLoaded,wasFailed=controller.failed();
        var result=controller.receive(reply);if(!result.accepted())return;
        if(result.failed()){status=controller.error();rebuildWidgets();return;}
        var metadata=controller.reply();if(metadata.has("preferences")&&metadata.get("preferences").isJsonObject()){preferences=metadata.getAsJsonObject("preferences");preferencesLoaded=true;}
        entries=controller.entries();
        if (target != null) {
            var notice = target; target = null;
            String id = Json.opt(notice, "target", "");
            if(Json.opt(notice,"routeSection","").equals("tasks"))minecraft.setScreen(new TaskScreen(parent,Json.opt(notice,"routeGroup",""),Json.opt(notice,"routeId","")));
            else if(Json.str(notice,"section").equals("home")&&!id.isEmpty())minecraft.setScreen(new TaskScreen(parent,"",id));
            else if (Json.str(notice, "section").equals("help") && !id.isEmpty()) FeatureListScreen.openReport(parent, id);
            else if (!id.isEmpty()) minecraft.setScreen(new CommunityScreen(parent, Json.str(notice, "section"), id));
            else onClose();
            return;
        }
        status = entries.isEmpty() ? (activeVote()?"":"Уведомлений пока нет") : "";
        if(result.changed()||hadPreferences!=preferencesLoaded||wasFailed)rebuildWidgets();
    }

    private void more(){if(controller.nextAllowed()&&firstRow+visibleRows>=entries.size())send("list","",controller.loadedPage()+1);}
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x < left() || x > left() + panelWidth()) return false;
        boolean used = super.mouseScrolled(x, y, dx, dy); if (dy < 0) more(); return used;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        boolean used = super.keyPressed(key, scan, modifiers); if (key == 267 || key == 269) more(); return used;
    }
    @Override public void tick(){
        if(!ServerMenuClient.available()){minecraft.setScreen(null);return;}
        if(controller.timeout()){status=controller.error();rebuildWidgets();}
        else if(controller.refreshDue())send("list","",controller.page()+1);
        else if(controller.dirtyDue()){controller.refresh();send("list","");}
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && (x < left() || x > left() + panelWidth() || y < top() || y > bottom())) { onClose(); return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float d) {UiDialog.surface(g,left(),top(),panelWidth(),bottom()-top());}
    @Override public void render(GuiGraphics g, int x, int y, float d) {
        UiDialog.render(parent,this,g,d,()->{
            super.render(g,x,y,d);
            UiHeading.dialog(g,font,title,left()+12,top(),panelWidth()-24);
            if(!status.isEmpty()||controller.busy())Ui.text(g,font,font.plainSubstrByWidth(controller.busy()?"Обновление…":status,panelWidth()-24),left()+12,top()+25,AccessibilityScreen.foreground(UiPalette.color(0xBAC7D2)));
        });
    }
    @Override public void onClose() { controller.leave();ServerMenuClient.cancelReads(this);if(parent instanceof CommunityScreen screen)screen.invalidate();else if(parent instanceof FeatureListScreen screen)screen.invalidate();minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
