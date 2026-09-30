package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
final class FollowingScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private final RequestSession session=new RequestSession();private JsonArray rows=new JsonArray();private boolean loaded,busy;private String notice="";
 FollowingScreen(Screen parent){super(Component.literal("Организаторы событий"));this.parent=parent;}
 private int w(){return Math.min(390,width-40);}private int x(){return (width-w())/2;}private int top(){return DialogPanel.top(height,300);}private int bottom(){return height-top();}
 private void request(String op,JsonObject j){if(busy)return;j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op",op);busy=true;ServerMenuClient.request(session.begin(j,!op.equals("toolsFollowing"),System.currentTimeMillis()));}
 @Override protected void init(){var add=addRenderableWidget(Button.builder(Component.literal("+ Подписаться на организатора"),b->FeatureListScreen.pick(this,p->{var j=new JsonObject();j.add("id",p.get("uuid"));request("toolsFollow",j);})).bounds(x(),top()+34,w(),20).build());add.active=!busy;scrollArea(rows.size(),top()+64,bottom()-58,26,x()+w()+4);for(int n=firstRow;n<Math.min(rows.size(),firstRow+visibleRows);n++){var p=rows.get(n).getAsJsonObject();var b=addRenderableWidget(Button.builder(Component.literal(Json.str(p,"name")+" · Отписаться"),v->{var j=new JsonObject();j.add("id",p.get("uuid"));j.addProperty("remove",true);request("toolsFollow",j);}).bounds(x(),top()+64+(n-firstRow)*26,w(),20).build());b.active=!busy;}addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x(),bottom()-28,w(),20).build());if(!loaded){loaded=true;request("toolsFollowing",new JsonObject());}}
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error"))notice=Json.opt(j,"text","Не удалось выполнить действие");else if(j.has("following")&&j.get("following").isJsonArray()){rows=j.getAsJsonArray("following");notice=rows.isEmpty()?"Подпишитесь, чтобы узнавать о новых событиях":"";}else{request("toolsFollowing",new JsonObject());return;}rebuildWidgets();}
 @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;notice="Нет ответа. Откройте список ещё раз.";rebuildWidgets();}}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){super.renderBackground(g,mx,my,d);DialogPanel.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());Ui.status(g,font,notice,x(),bottom()-54,w(),bottom()-30);}
 @Override public void onClose(){session.cancel();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
