package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Read-only role inspection: never changes the administrator's identity or rights. */
final class RolePreviewScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private final RequestSession session=new RequestSession();private JsonArray roles=new JsonArray();private boolean loaded,busy;private int selected;private String notice="";
 RolePreviewScreen(Screen parent){super(Component.literal("Права роли · предпросмотр"));this.parent=parent;}
 private int w(){return Math.min(430,width-32);}private int x(){return (width-w())/2;}private int top(){return UiDialog.top(height,350);}private int end(){return height-top();}
 private void load(){var j=new JsonObject();j.addProperty("action","rolePreview");busy=true;ServerMenuClient.request(session.begin(j,false,System.currentTimeMillis()));}
 @Override protected void init(){if(!roles.isEmpty()){selected=Math.min(selected,roles.size()-1);var role=roles.get(selected).getAsJsonObject();addRenderableWidget(UiActions.button(Component.literal(Json.str(role,"name")+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,"Выберите роль",roles.asList().stream().map(e->Json.str(e.getAsJsonObject(),"name")).toList(),n->{selected=n;resetScroll();rebuildWidgets();},b).current(selected))).bounds(x()+12,top()+36,w()-24,20).build());var caps=role.getAsJsonObject("capabilities");var keys=new ArrayList<>(caps.keySet());scrollArea(keys.size(),new dev.abros.anthub.core.NativeLayout.Box(x()+12,top()+70,Math.max(0,w()-24),Math.max(0,(end()-72)-(top()+70))),26);for(int n=firstRow;n<Math.min(keys.size(),firstRow+visibleRows);n++){String key=keys.get(n);addRenderableWidget(new UiChoiceRow(x()+12,top()+70+(n-firstRow)*26,w()-24,label(key),caps.get(key).getAsBoolean(),-1,UiKit.ACCENT,()->{})).active=false;}}
  UiActions.close(new dev.abros.anthub.core.NativeLayout.Box(x(),end()-28,w()-12,20),this::addRenderableWidget,this::onClose);if(!loaded){loaded=true;load();}}
 private static String label(String key){return switch(key){case "anthub.admin"->"Полное администрирование";case "anthub.events"->"Создание событий";case "anthub.auth.reset"->"Сброс авторизации";case "anthub.vote.protected"->"Защита от голосования о наказании";case "anthub.stats.edit"->"Изменение статистики";case "anthub.stats.view"->"Просмотр статистики";case "anthub.announce"->"Объявления";case "anthub.maintenance"->"Обслуживание сервера";case "anthub.restart"->"Остановка сервера";case "anthub.reports"->"Работа с обращениями";case "anthub.diagnostics"->"Диагностика клиентов";default->key;};}
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error"))notice=Json.opt(j,"text","Недоступно");else if(j.has("roles")){roles=j.getAsJsonArray("roles");notice=roles.isEmpty()?"Не удалось получить загруженные роли LuckPerms. Проверьте подключение плагина.":Json.opt(j,"context","");}rebuildWidgets();}
 @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;notice="Нет ответа. Откройте заново.";rebuildWidgets();}if(!ServerMenuClient.admin())onClose();}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),end());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x()+12,top(),w()-24);Ui.status(g,font,busy?"Загрузка ролей…":notice,x()+12,end()-66,w()-24,end()-34);});}
 @Override public void onClose(){session.cancel();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
