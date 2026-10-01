package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
final class GlobalSearchScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private String query="",notice="Введите хотя бы два символа";private JsonArray entries=new JsonArray();private final RequestSession session=new RequestSession();private boolean busy;private long changed;
 GlobalSearchScreen(Screen parent){super(Component.literal("Поиск по серверу"));this.parent=parent;}
 private int w(){return Math.min(470,width-40);}private int x(){return (width-w())/2;}private int top(){return DialogPanel.top(height,330);}private int bottom(){return height-top();}
 private void search(){if(busy)return;if(query.strip().length()<2){changed=0;notice="Введите хотя бы два символа";return;}var j=new JsonObject();j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op","globalSearch");j.addProperty("query",query);busy=true;notice="Поиск…";changed=0;ServerMenuClient.request(session.begin(j,false,System.currentTimeMillis()));}
 @Override protected void init(){var edit=addRenderableWidget(new UiEditBox(font,x(),top()+32,w()-64,20,Component.literal("Поиск")));edit.setMaxLength(100);edit.setValue(query);edit.setResponder(v->{query=v;changed=System.currentTimeMillis()+500;});addRenderableWidget(Button.builder(Component.literal("Найти"),b->search()).bounds(x()+w()-58,top()+32,58,20).build());scrollArea(entries.size(),top()+62,bottom()-54,26,x()+w()+4);for(int n=firstRow;n<Math.min(entries.size(),firstRow+visibleRows);n++){var e=entries.get(n).getAsJsonObject();addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth((Json.str(e,"section").equals("players")?"Игрок":Json.str(e,"section").equals("tasks")?"Задача":CommunityScreen.name(Json.str(e,"section")))+" · "+Json.str(e,"title"),w()-20)),b->{String section=Json.str(e,"section");if(section.equals("players"))FeatureListScreen.searchPlayer(this,Json.str(e,"title"));else if(section.equals("tasks"))minecraft.setScreen(new TaskScreen(this,Json.opt(e,"group",""),Json.str(e,"id")));else minecraft.setScreen(new CommunityScreen(this,section,Json.str(e,"id")));}).bounds(x(),top()+62+(n-firstRow)*26,w(),22).build());}addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x(),bottom()-28,w(),20).build());}
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;notice=j.has("error")?Json.opt(j,"text","Поиск недоступен"):j.getAsJsonArray("entries").isEmpty()?"Ничего не найдено":"Найдено: "+j.getAsJsonArray("entries").size();if(j.has("entries")&&!entries.equals(j.get("entries"))){entries=j.getAsJsonArray("entries");resetScroll();rebuildWidgets();}}
 @Override public void tick(){if(changed>0&&System.currentTimeMillis()>=changed&&!busy)search();if(session.timeout(System.currentTimeMillis())){busy=false;notice="Нет ответа. Нажмите «Найти».";}}
 @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257){search();return true;}return super.keyPressed(key,scan,modifiers);}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){super.renderBackground(g,mx,my,d);DialogPanel.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());Ui.text(g,font,notice,x(),bottom()-46,UiPalette.color(0xBAC7D2));}
 @Override public void onClose(){session.cancel();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
