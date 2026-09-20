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
    private final NavigableMap<Integer, JsonArray> pages = new TreeMap<>();
    private final Map<Integer,String> cursors=new HashMap<>();
    private JsonArray entries = new JsonArray();
    private String request = "", status = "";
    private boolean started, busy, more, failed;
    private final dev.abros.anthub.core.RequestSession session=new dev.abros.anthub.core.RequestSession();
    private int page, loadedPage, refreshThrough;
    private long sent, dirtyAt, nextAt;
    private JsonObject target;

    NotificationPopup(Screen parent) { super(Component.literal("Уведомления")); this.parent = parent; }
    void invalidate() { if (dirtyAt == 0) dirtyAt = System.currentTimeMillis(); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return Math.max(8, (height - 320) / 2); }
    private int bottom() { return Math.min(height - 8, top() + 320); }
    private int panelWidth() { return Math.min(410, width - 24); }

    @Override protected void init() {
        ModalLayer.prepare(parent,this);
        int x = left(), w = panelWidth(), y = top();
        scrollArea(entries.size(), y + 40, bottom() - 86, 36, x + w - 10);
        for (int i = firstRow; i < Math.min(entries.size(), firstRow + visibleRows); i++) {
            var notice = entries.get(i).getAsJsonObject();
            String label = (notice.get("read").getAsBoolean() ? "" : "● ") + Json.str(notice, "title");
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label, w - 40)), b -> {
                if (busy) return;
                target = notice;
                page = 0;
                send("read", Json.str(notice, "id"));
            }).bounds(x + 10, y + 40 + (i - firstRow) * 36, w - 28, 30).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Прочитать всё"), b -> {
            if (busy) return;
            target = null;
            refreshThrough = loadedPage;
            page = 0;
            send("read", "");
        }).bounds(x + 10, bottom() - 76, (w - 24)/2, 20).build());
        var retry=addRenderableWidget(Button.builder(Component.literal("Повторить"),b->{if(busy||!failed)return;failed=false;busy=true;sent=System.currentTimeMillis();var j=session.retry(sent);request=Json.str(j,"request");ServerMenuClient.request(j);rebuildWidgets();}).bounds(x+14+(w-24)/2,bottom()-76,(w-24)/2,20).build());retry.active=failed&&!busy;
        int half = (w - 24) / 2;
        addRenderableWidget(Button.builder(Component.literal("Звук: " + (ServerMenuClient.enabled(1) ? "вкл" : "выкл")), b -> {
            ServerMenuClient.toggle(1); rebuildWidgets();
        }).bounds(x + 10, bottom() - 52, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Плашки: " + (ServerMenuClient.enabled(0) ? "вкл" : "выкл")), b -> {
            ServerMenuClient.toggle(0); rebuildWidgets();
        }).bounds(x + 14 + half, bottom() - 52, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose()).bounds(x + 10, bottom() - 28, w - 20, 20).build());
        if (!started) { started = true; send("list", ""); }
    }

    private void send(String op, String id) {
        if (busy) return;
        busy = true;failed=false;
        sent = System.currentTimeMillis();
        request = UUID.randomUUID().toString();
        var j = new JsonObject();
        j.addProperty("action", "community"); j.addProperty("section", "notifications");
        j.addProperty("cursor",page==0?"":cursors.getOrDefault(page,""));j.addProperty("op", op); j.addProperty("id", id); j.addProperty("page", page); j.addProperty("request", request);
        j=session.begin(j,!op.equals("list"),sent);request=Json.str(j,"request");ServerMenuClient.request(j);
    }

    @Override public void receiveCommunity(JsonObject j) {
        if (!session.receive(j)) return;
        busy = false;
        if (j.has("error")) { status = Json.opt(j, "text", "Ошибка"); failed=true; nextAt = 0; rebuildWidgets();return; }
        JsonArray found = j.getAsJsonArray("entries");
        pages.put(page, found.deepCopy());
        String next=Json.opt(j,"nextCursor","");cursors.put(page+1,next);
        loadedPage = Math.max(loadedPage, page);
        if (next.isEmpty()) { pages.tailMap(page, false).clear(); loadedPage = page; refreshThrough = Math.min(refreshThrough, page); }
        if (page == loadedPage) more = !next.isEmpty();
        nextAt = page < refreshThrough ? System.currentTimeMillis() + 600 : 0;
        entries = new JsonArray();
        var ids = new HashSet<String>();
        for (var batch : pages.values()) for (var entry : batch) if (ids.add(Json.str(entry.getAsJsonObject(), "id"))) entries.add(entry);
        if (target != null) {
            var notice = target; target = null;
            String id = Json.opt(notice, "target", "");
            if (Json.str(notice, "section").equals("help") && !id.isEmpty()) FeatureListScreen.openReport(parent, id);
            else if (!id.isEmpty()) minecraft.setScreen(new CommunityScreen(parent, Json.str(notice, "section"), id));
            else onClose();
            return;
        }
        status = entries.isEmpty() ? "Событий пока нет" : "";
        rebuildWidgets();
    }

    private void more() {
        if (more && !busy && !failed && nextAt == 0 && firstRow + visibleRows >= entries.size() && System.currentTimeMillis() - sent > 600) {
            page = loadedPage + 1; send("list", "");
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x < left() || x > left() + panelWidth()) return false;
        boolean used = super.mouseScrolled(x, y, dx, dy); if (dy < 0) more(); return used;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        boolean used = super.keyPressed(key, scan, modifiers); if (key == 267 || key == 269) more(); return used;
    }
    @Override public void tick() {
        long now = System.currentTimeMillis();
        if (!ServerMenuClient.available()) { minecraft.setScreen(null); return; }
        if (session.timeout(now)) { busy = false;failed=true; nextAt = 0; status = "Нет ответа. Нажмите «Повторить»."; rebuildWidgets();}
        else if (!busy && !failed && nextAt > 0 && now >= nextAt) { nextAt = 0; page++; send("list", ""); }
        else if (!busy && !failed && dirtyAt > 0 && now - sent > 750) { dirtyAt = 0; refreshThrough = loadedPage; page = 0; send("list", ""); }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && (x < left() || x > left() + panelWidth() || y < top() || y > bottom())) { onClose(); return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float d) {
        g.fill(0, 0, width, height, 0xAA090E14); g.fill(left(), top(), left() + panelWidth(), bottom(), 0xF51B252E);
        g.renderOutline(left(), top(), panelWidth(), bottom() - top(), 0xFF536879);
    }
    @Override public void render(GuiGraphics g, int x, int y, float d) {
        ModalLayer.render(parent,this,g,d,()->{
            super.render(g,x,y,d);
            g.drawCenteredString(font,title,width/2,top()+10,0xE2BE75);
            if(!status.isEmpty()||busy)g.drawString(font,font.plainSubstrByWidth(busy?"Обновление…":status,panelWidth()-24),left()+12,top()+25,AccessibilityScreen.foreground(0xBAC7D2));
        });
    }
    @Override public void onClose() { if(parent instanceof CommunityScreen screen)screen.invalidate();else if(parent instanceof FeatureListScreen screen)screen.invalidate();minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
