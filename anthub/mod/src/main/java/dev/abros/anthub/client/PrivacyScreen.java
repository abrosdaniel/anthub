package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
final class PrivacyScreen extends Screen implements CommunityScreen.Receiver {
 private final Screen parent;private String mode="public",notice="";private boolean statistics=true,loaded,busy;private final RequestSession session=new RequestSession();
 PrivacyScreen(Screen parent){super(Component.literal("Видимость профиля"));this.parent=parent;}
 private int top(){return UiDialog.top(height,200);}private int bottom(){return height-top();}private int w(){return Math.min(380,width-32);}private int x(){return (width-w())/2;}
 private void request(boolean save){if(busy)return;var j=new JsonObject();j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op",save?"toolsPrivacySave":"toolsPrivacy");j.addProperty("mode",mode);j.addProperty("statistics",statistics);busy=true;ServerMenuClient.request(session.begin(j,save,System.currentTimeMillis()));}
 @Override protected void init(){var modes=List.of("public","members","private");var names=List.of("Все игроки","Мои объединения","Только я");var choice=addRenderableWidget(UiActions.button(Component.literal("О себе: "+names.get(modes.indexOf(mode))+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,"Кто видит описание",names,n->{mode=modes.get(n);rebuildWidgets();},b))).bounds(x(),top()+36,w(),20).build());choice.active=!busy;addRenderableWidget(UiActions.button(Component.literal("Показывать статистику: "+(statistics?"да":"нет")),UiActions.Tone.NORMAL,"",b->{statistics=!statistics;rebuildWidgets();}).bounds(x(),top()+64,w(),20).build());var save=addRenderableWidget(UiActions.button(Component.literal("Сохранить"),UiActions.Tone.PRIMARY,UiIcons.SAVE,b->request(true)).bounds(x(),bottom()-54,w(),20).build());save.active=!busy&&loaded;UiActions.close(new dev.abros.anthub.core.NativeLayout.Box(x(),bottom()-28,w(),20),this::addRenderableWidget,this::onClose);if(!loaded){loaded=true;request(false);}}
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;notice=j.has("error")?Json.opt(j,"text","Не удалось сохранить"):"Сохранено";if(j.has("settings")){mode=Json.opt(j.getAsJsonObject("settings"),"mode","public");statistics=j.getAsJsonObject("settings").get("statistics").getAsBoolean();}rebuildWidgets();}
 @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;notice="Нет ответа. Повторите сохранение.";rebuildWidgets();}}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());Ui.text(g,font,notice,x(),bottom()-78,UiPalette.color(0xBAC7D2));});}
 @Override public void onClose(){if(!busy){session.cancel();minecraft.setScreen(parent);}}
 @Override public boolean isPauseScreen(){return false;}
}
