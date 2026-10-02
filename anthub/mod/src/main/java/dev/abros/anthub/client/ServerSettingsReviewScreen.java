package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
final class ServerSettingsReviewScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private final RequestSession session=new RequestSession();private JsonObject data=new JsonObject();private boolean loaded,busy;private String notice="";private final List<String> lines=new ArrayList<>();
 ServerSettingsReviewScreen(Screen parent){super(Component.literal("Изменения настроек сервера"));this.parent=parent;}
 private int w(){return Math.min(480,width-32);}private int x(){return(width-w())/2;}private int top(){return UiDialog.top(height,340);}private int bottom(){return height-top();}
 private void request(boolean apply){if(busy)return;var j=new JsonObject();j.addProperty("action","serverConfig");j.addProperty("op",apply?"apply":"preview");if(apply)j.add("hash",data.get("hash"));busy=true;ServerMenuClient.request(session.begin(j,apply,System.currentTimeMillis()));rebuildWidgets();}
 @Override protected void init(){lines.clear();if(data.has("changes"))for(var e:data.getAsJsonArray("changes")){var c=e.getAsJsonObject();String text=Json.str(c,"key")+" · "+(c.get("live").getAsBoolean()?"можно применить":"нужен перезапуск");if(c.has("after"))text+=" → "+c.get("after");for(var line:font.getSplitter().splitLines(text,w()-14,net.minecraft.network.chat.Style.EMPTY))lines.add(line.getString());}if(data.has("changes")&&lines.isEmpty())lines.add("Изменений нет");scrollArea(lines.size(),new dev.abros.anthub.core.NativeLayout.Box(x(),top()+36,Math.max(0,w()),Math.max(0,(bottom()-88)-(top()+36))),14);var apply=addRenderableWidget(UiActions.button(Component.literal("Применить меню и разделы"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(yes)request(true);},Component.literal("Применить настройки?"),Component.literal("Применятся только меню и разделы. Остальные изменения требуют перезапуска.")))).bounds(x(),bottom()-78,w(),20).build());apply.active=!busy&&data.has("changes")&&java.util.stream.StreamSupport.stream(data.getAsJsonArray("changes").spliterator(),false).anyMatch(e->e.getAsJsonObject().get("live").getAsBoolean());var refresh=addRenderableWidget(UiActions.button(Component.literal("Проверить файл заново"),UiActions.Tone.NORMAL,"",b->request(false)).bounds(x(),bottom()-54,w(),20).build());refresh.active=!busy;UiActions.close(new dev.abros.anthub.core.NativeLayout.Box(x(),bottom()-28,w(),20),this::addRenderableWidget,this::onClose);if(!loaded){loaded=true;request(false);}}
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;notice=j.has("error")?Json.opt(j,"text","Не удалось проверить"):j.has("applied")?"Меню и разделы обновлены":"";if(j.has("changes"))data=j;rebuildWidgets();}
 @Override public void tick(){if(!ServerMenuClient.admin())onClose();if(session.timeout(System.currentTimeMillis())){busy=false;notice="Нет ответа. Проверьте файл заново.";rebuildWidgets();}}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());for(int i=firstRow;i<Math.min(lines.size(),firstRow+visibleRows);i++)Ui.text(g,font,lines.get(i),x()+4,top()+36+(i-firstRow)*14,UiPalette.color(0xD7E2EC));Ui.status(g,font,notice,x(),bottom()-103,w(),bottom()-80);});}
 @Override public void onClose(){session.cancel();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
