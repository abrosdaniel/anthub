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
 private final Screen parent;private final JsonArray muted=new JsonArray();private final RequestSession session=new RequestSession();private boolean busy,retry;private String status="";
 CommunityPreferences(Screen parent,JsonObject prefs){super(Component.literal("Настройки уведомлений"));this.parent=parent;if(prefs.has("muted"))prefs.getAsJsonArray("muted").forEach(muted::add);}
 private void toggle(String key){if(busy||retry)return;var value=new JsonPrimitive(key);if(muted.contains(value))muted.remove(value);else muted.add(value);var body=new JsonObject();body.addProperty("action","community");body.addProperty("op","preferences");body.addProperty("section","home");var prefs=new JsonObject();prefs.add("muted",muted.deepCopy());body.add("preferences",prefs);busy=true;status="Сохранение…";ServerMenuClient.request(session.begin(body,true,System.currentTimeMillis()));rebuildWidgets();}
 private int panelTop(){return DialogPanel.top(height,340);}
 private int panelBottom(){return height-panelTop();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){super.renderBackground(g,x,y,d);DialogPanel.draw(g,width,Math.min(440,width-32),panelTop(),panelBottom());}
 @Override protected void init(){int w=Math.min(440,width-32),x=(width-w)/2,top=panelTop();var keys=List.of("board","groups","events","polls","ideas","notices","sound","restartNotices","contacts");scrollArea(keys.size(),top+30,panelBottom()-78,26,x+w+4);for(int n=firstRow;n<Math.min(keys.size(),firstRow+visibleRows);n++){String key=keys.get(n);int y=top+30+(n-firstRow)*26;if(n==8){var contacts=addRenderableWidget(Button.builder(Component.literal("Приглашения и отклики…"),b->PersonalProfileScreen.ignores(this,"")).bounds(x,y,w,20).build());contacts.active=!busy&&!retry;}else if(n>=5){int setting=n==6?1:n==7?2:0;addRenderableWidget(Button.builder(Component.literal((setting==0?"Все плашки":setting==1?"Звук уведомлений":"Предупреждения о перезапуске")+": "+(ServerMenuClient.enabled(setting)?"вкл":"выкл")),b->{ServerMenuClient.toggle(setting);rebuildWidgets();}).bounds(x,y,w,20).build());}else{var button=addRenderableWidget(Button.builder(Component.literal(CommunityScreen.name(key)+": "+(muted.contains(new JsonPrimitive(key))?"без плашек":"плашки включены")),b->toggle(key)).bounds(x,y,w,20).build());button.active=!busy&&!retry;}}
  if(retry)addRenderableWidget(Button.builder(Component.literal("Повторить сохранение"),b->{busy=true;retry=false;status="Сохранение…";ServerMenuClient.request(session.retry(System.currentTimeMillis()));rebuildWidgets();}).bounds(x,panelBottom()-54,w,20).build());
  var back=addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x,panelBottom()-28,w,20).build());back.active=!busy;
 }
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error")){status=Json.opt(j,"text","Не удалось сохранить");retry=true;}else{status="Сохранено";retry=false;invalidateParent();}rebuildWidgets();}
 @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;retry=true;status="Нет ответа. Повторите сохранение.";rebuildWidgets();}}
 @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,panelTop()+6,0xE2BE75);Ui.status(g,font,status,(width-Math.min(440,width-32))/2,panelBottom()-76,Math.min(440,width-32),panelBottom()-56);}
 @Override public void onClose(){if(busy)return;session.cancel();invalidateParent();minecraft.setScreen(parent);}
 private void invalidateParent(){if(parent instanceof NotificationPopup p)p.invalidate();else if(parent instanceof CommunityScreen p)p.invalidate();}
 @Override public boolean isPauseScreen(){return false;}
}
