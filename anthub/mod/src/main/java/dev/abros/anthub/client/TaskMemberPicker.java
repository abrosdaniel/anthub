package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
final class TaskMemberPicker extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private final String group;private final Consumer<JsonObject> selected;private final RequestSession session=new RequestSession();private JsonArray rows=new JsonArray();private boolean busy,loaded;private String query="",cursor="",next="",notice="";private long changed;
 TaskMemberPicker(Screen parent,String group,Consumer<JsonObject> selected){super(Component.literal("Ответственный · участник объединения"));this.parent=parent;this.group=group;this.selected=selected;}
 private int w(){return Math.min(390,width-40);}private int x(){return (width-w())/2;}private int top(){return UiDialog.top(height,320);}private int bottom(){return height-top();}
 private void load(){if(busy)return;busy=true;changed=0;var j=new JsonObject();j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op","workMembers");j.addProperty("group",group);j.addProperty("query",query);j.addProperty("cursor",cursor);ServerMenuClient.request(session.begin(j,false,System.currentTimeMillis()));}
 @Override protected void init(){var search=addRenderableWidget(UiFields.text(font,x(),top()+32,w(),20,Component.literal("Поиск участника")));search.setHint(Component.literal("Поиск по нику"));search.setMaxLength(32);search.setValue(query);search.setResponder(v->{query=v;cursor="";changed=System.currentTimeMillis()+500;});scrollArea(rows.size(),new dev.abros.anthub.core.NativeLayout.Box(x(),top()+62,Math.max(0,w()),Math.max(0,(bottom()-66)-(top()+62))),26);for(int n=firstRow;n<Math.min(rows.size(),firstRow+visibleRows);n++){var row=rows.get(n).getAsJsonObject();var b=addRenderableWidget(UiActions.button(Component.literal(Json.str(row,"name")),UiActions.Tone.NORMAL,"",v->{if(busy||changed!=0)return;minecraft.setScreen(parent);selected.accept(row);}).bounds(x(),top()+62+(n-firstRow)*26,w(),20).build());b.active=!busy&&changed==0;}if(!next.isEmpty()){var b=addRenderableWidget(UiActions.button(Component.literal("Следующая страница"),UiActions.Tone.NORMAL,"",v->{if(busy||changed!=0)return;cursor=next;load();}).bounds(x(),bottom()-54,w(),20).build());b.active=!busy;}UiActions.close(new dev.abros.anthub.core.NativeLayout.Box(x(),bottom()-28,w(),20),this::addRenderableWidget,this::onClose);if(!loaded){loaded=true;load();}}
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error"))notice=Json.opt(j,"text","Недоступно");else{rows=j.getAsJsonArray("members");next=Json.opt(j,"nextCursor","");notice=rows.isEmpty()?"Участник не найден":"";}resetScroll();rebuildWidgets();}
 @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;notice="Нет ответа. Измените поиск и повторите.";rebuildWidgets();}if(changed>0&&System.currentTimeMillis()>=changed&&!busy)load();}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());Ui.status(g,font,notice,x(),bottom()-64,w(),bottom()-30);});}
 @Override public void onClose(){session.cancel();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
