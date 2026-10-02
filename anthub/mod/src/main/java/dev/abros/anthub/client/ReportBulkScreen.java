package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Server-computed preview before applying a bounded administrative batch. */
final class ReportBulkScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final ReportQueueScreen parent;private final String change;private final JsonArray items;private final RequestSession session=new RequestSession();private JsonObject data=new JsonObject();private boolean loaded,busy;private String notice="";
 ReportBulkScreen(ReportQueueScreen parent,String change,JsonArray items){super(Component.literal("Проверка массового действия"));this.parent=parent;this.change=change;this.items=items;}
 private int w(){return Math.min(420,width-32);}private int x(){return (width-w())/2;}private int top(){return UiDialog.top(height,320);}private int end(){return height-top();}
 private void send(boolean apply){if(busy)return;var j=new JsonObject();j.addProperty("action","reportManage");j.addProperty("operation",apply?"bulkApply":"bulkPreview");j.addProperty("change",change);j.add("items",items);busy=true;ServerMenuClient.request(session.begin(j,apply,System.currentTimeMillis()));rebuildWidgets();}
 @Override protected void init(){var rows=data.has("preview")?data.getAsJsonArray("preview"):new JsonArray();scrollArea(rows.size(),new dev.abros.anthub.core.NativeLayout.Box(x()+12,top()+88,Math.max(0,w()-24),Math.max(0,(end()-74)-(top()+88))),28);for(int n=firstRow;n<Math.min(rows.size(),firstRow+visibleRows);n++){var row=rows.get(n).getAsJsonObject();addRenderableWidget(UiActions.button(Component.literal(font.plainSubstrByWidth(Json.str(row,"title")+" · "+(Json.str(row,"reason").isEmpty()?"Будет изменено":Json.str(row,"reason")),w()-36)),UiActions.Tone.NORMAL,"",b->{}).bounds(x()+12,top()+88+(n-firstRow)*28,w()-24,22).build()).active=false;}
  addRenderableWidget(UiActions.button(Component.literal("Применить"),UiActions.Tone.NORMAL,"",b->send(true)).bounds(x()+12,end()-28,110,20).build()).active=!busy&&data.has("affected")&&data.get("affected").getAsInt()>0;addRenderableWidget(UiActions.button(Component.literal("Отмена"),UiActions.Tone.NORMAL,"",b->onClose()).bounds(x()+w()-102,end()-28,90,20).build());if(!loaded){loaded=true;send(false);}}
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error"))notice=Json.opt(j,"text","Не удалось выполнить");else{data=j;if(j.has("applied")&&j.get("applied").getAsBoolean()){minecraft.setScreen(parent);parent.invalidate();return;}}rebuildWidgets();}
 @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;notice="Нет ответа. Закройте окно и обновите очередь перед повтором.";data=new JsonObject();rebuildWidgets();}}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),end());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x()+12,top(),w()-24);Ui.text(g,font,switch(change){case "claim"->"Взять в работу";case "resolved"->"Закрыть выбранные обращения";default->"Повысить приоритет";},x()+12,top()+40,UiKit.text());Ui.text(g,font,data.has("affected")?"Изменится: "+data.get("affected")+" · Пропущено: "+data.get("skipped"):"Проверяем текущие данные…",x()+12,top()+62,UiKit.muted());Ui.status(g,font,notice,x()+12,end()-64,w()-24,end()-34);});}
 @Override public void onClose(){session.cancel();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
