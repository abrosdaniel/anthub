package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Toggles persist immediately. Failed network saves keep the same command for safe retry. */
final class CommunityPreferences extends ScrollScreen implements CommunityScreen.Receiver {
 private JsonObject preferences;private final Screen parent;private final JsonArray muted=new JsonArray();private final RequestSession session=new RequestSession();private boolean busy,retry;private boolean delivery;private String status="";private final List<String> rowLabels=new ArrayList<>();
 CommunityPreferences(Screen parent,JsonObject prefs){super(Component.literal("Настройки уведомлений"));this.parent=parent;this.preferences=prefs.deepCopy();if(prefs.has("muted"))prefs.getAsJsonArray("muted").forEach(muted::add);}
 private void toggle(String key){if(busy||retry)return;var value=new JsonPrimitive(key);if(muted.contains(value))muted.remove(value);else muted.add(value);var body=new JsonObject();body.addProperty("action","community");body.addProperty("op","preferences");body.addProperty("section","home");var prefs=preferences.deepCopy();prefs.add("muted",muted.deepCopy());body.add("preferences",prefs);busy=true;status="Сохранение…";ServerMenuClient.request(session.begin(body,true,System.currentTimeMillis()));rebuildWidgets();}
 private void snooze(){preferences.addProperty("snoozeUntil",preferences.has("snoozeUntil")&&preferences.get("snoozeUntil").getAsLong()>System.currentTimeMillis()?0:System.currentTimeMillis()+3600000);var j=new JsonObject();j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op","preferences");var p=preferences.deepCopy();p.add("muted",muted.deepCopy());j.add("preferences",p);busy=true;ServerMenuClient.request(session.begin(j,true,System.currentTimeMillis()));rebuildWidgets();}
 private int panelTop(){return UiDialog.top(height,280);}
 private int panelBottom(){return height-panelTop();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,contentWidth()+24,panelTop(),panelBottom());}
 private int contentWidth(){return Math.min(360,width-48);}
 @Override protected void init(){rowLabels.clear();int w=contentWidth(),x=(width-w)/2,top=panelTop();
  UiTabs.build(this,font,new dev.abros.anthub.core.NativeLayout.Box(x,top+30,w,20),List.of("По разделам","Доставка"),delivery?1:0,this::addRenderableWidget,n->{delivery=n==1;resetScroll();rebuildWidgets();},!busy);
  scrollArea(delivery?6:5,new dev.abros.anthub.core.NativeLayout.Box(x,top+68,Math.max(0,w),Math.max(0,(panelBottom()-60)-(top+68))),30);
  for(int n=firstRow;n<Math.min(delivery?6:5,firstRow+visibleRows);n++){final int index=n;int y=top+68+(n-firstRow)*30;
   String label,value;Runnable change;
   if(!delivery){String key=List.of("board","groups","events","polls","ideas").get(n);label=CommunityScreen.name(key);value=muted.contains(new JsonPrimitive(key))?"Нет":"Да";change=()->toggle(key);}
   else if(n<3){int setting=n==1?1:n==2?2:0;label=List.of("Плашки в игре","Звук","Перезапуск сервера").get(n);value=ServerMenuClient.enabled(setting)?"Да":"Нет";change=()->{ServerMenuClient.toggle(setting);rebuildWidgets();};}
   else if(n==3){label="Тихий час · 1 час";value=preferences.has("snoozeUntil")&&preferences.get("snoozeUntil").getAsLong()>System.currentTimeMillis()?"Вкл":"Выкл";change=this::snooze;}
   else if(n==5){label="Оформление и фильтры плашек";value="Настроить…";change=()->minecraft.setScreen(new HudSettingsScreen(this));}
   else{label="Приглашения и отклики";value="Настроить…";change=()->PersonalProfileScreen.ignores(this,"");}
   rowLabels.add(label);var button=delivery&&index>=4?addRenderableWidget(UiActions.button(Component.literal(value),UiActions.Tone.NORMAL,"",b->change.run()).bounds(x+w-102,y,86,24).build()):addRenderableWidget(new UiToggle(value,x+w-102,y,86,value.equals("Да")||value.equals("Вкл"),change));button.setTooltip(Tooltip.create(Component.literal(label)));button.active=delivery&&index<3||!busy&&!retry;
  }
  if(retry)addRenderableWidget(UiActions.button(Component.literal("Повторить сохранение"),UiActions.Tone.NORMAL,UiIcons.REFRESH,b->{busy=true;retry=false;status="Сохранение…";ServerMenuClient.request(session.retry(System.currentTimeMillis()));rebuildWidgets();}).bounds(x,panelBottom()-52,w-100,20).build());
  addRenderableWidget(UiActions.button(Component.literal("Готово"),UiActions.Tone.NORMAL,"",b->onClose()).bounds(x+w-80,panelBottom()-28,80,20).build()).active=!busy;
 }
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error")){status=Json.opt(j,"text","Не удалось сохранить");retry=true;}else{status="Сохранено";retry=false;invalidateParent();}rebuildWidgets();}
 @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;retry=true;status="Нет ответа. Повторите сохранение.";rebuildWidgets();}}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);int left=(width-contentWidth())/2;for(int n=0;n<rowLabels.size();n++){int yy=panelTop()+68+n*30;g.fill(left,yy,left+contentWidth()-110,yy+24,UiPalette.color(0xFF172630));Ui.text(g,font,font.plainSubstrByWidth(rowLabels.get(n),contentWidth()-126),left+8,yy+8,UiPalette.color(0xD8E9F2),false);}UiHeading.dialog(g,font,title,(width-(contentWidth()))/2,panelTop(),contentWidth());Ui.status(g,font,status.isEmpty()?(delivery?"Настройки действуют на этом клиенте":"Уведомления выбранных разделов · сохранение автоматически"):status,(width-contentWidth()+24)/2,panelBottom()-56,contentWidth()+24,panelBottom()-34);});}
 @Override public void onClose(){if(busy)return;session.cancel();invalidateParent();minecraft.setScreen(parent);}
 private void invalidateParent(){if(parent instanceof NotificationPopup p)p.invalidate();else if(parent instanceof CommunityScreen p)p.invalidate();}
 @Override public boolean isPauseScreen(){return false;}
}
