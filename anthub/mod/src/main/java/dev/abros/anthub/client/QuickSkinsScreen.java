package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
/** Small selector; changes use the same acknowledged, idempotent skin requests. */
final class QuickSkinsScreen extends ScrollScreen {
 private final Screen parent;private String expected;private Object connection;private boolean loaded;
 QuickSkinsScreen(Screen parent){super(Component.literal("Быстрая смена скина"));this.parent=parent;}
 private int w(){return Math.min(300,width-24);}private int x(){return (width-w())/2;}private int top(){return DialogPanel.top(height,Math.min(340,Math.max(190,112+(entries().size()+1)*42)));}private int bottom(){return height-top();}
 private JsonArray entries(){return SkinClient.library.has("entries")?SkinClient.library.getAsJsonArray("entries"):new JsonArray();}
 private String active(){return SkinClient.library.has("profile")?Json.opt(SkinClient.library.getAsJsonObject("profile"),"active",""):"";}
 void updated(){if(expected!=null&&!SkinClient.busy&&expected.equals(active())){onClose();return;}rebuildWidgets();}
 @Override protected void init(){if(!loaded){loaded=true;connection=minecraft.getConnection();if(!SkinClient.busy)SkinClient.command("list","",false);}scrollArea(entries().size()+1,top()+36,bottom()-76,42,x()+w()+4);
  for(int n=firstRow;n<Math.min(entries().size()+1,firstRow+visibleRows);n++){var entry=n==0?null:entries().get(n-1).getAsJsonObject();String id=entry==null?"":Json.str(entry,"id"),name=entry==null?"Обычный скин":Json.str(entry,"name");boolean selected=id.equals(active());int y=top()+36+(n-firstRow)*42;
   var button=addRenderableWidget(new Button(x(),y,w(),36,Component.literal(name+(selected?" · выбран":"")),b->{if(!SkinClient.busy){expected=id;SkinClient.command("select",id,false);rebuildWidgets();}},supplier->Component.literal(name+(selected?" · выбран":""))){
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float d){int xx=getX(),yy=getY();g.fill(xx,yy,xx+getWidth(),yy+getHeight(),isHoveredOrFocused()?UiPalette.color(0xFF344657):UiPalette.color(0xFF202D38));g.renderOutline(xx,yy,getWidth(),getHeight(),selected?UiPalette.color(0xFFE2BE75):UiPalette.color(0xFF526674));PlayerSkin face=SkinClient.ordinarySkin();if(entry!=null){var texture=SkinClient.texture(Json.str(entry,"hash"));if(texture!=null)face=new PlayerSkin(texture,null,null,null,entry.get("slim").getAsBoolean()?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE,false);}PlayerFaceRenderer.draw(g,face,xx+6,yy+6,24);g.drawString(font,font.plainSubstrByWidth(name,getWidth()-44),xx+38,yy+7,0xFFFFFF);g.drawString(font,selected?"Выбран":"Выбрать",xx+38,yy+20,selected?UiPalette.color(0xE2BE75):UiPalette.color(0xBAC7D2));}
   });button.active=!SkinClient.busy&&!selected;
  }
  var manage=addRenderableWidget(Button.builder(Component.literal("Управление скинами"),b->SkinsScreen.open(this)).bounds(x(),bottom()-28,Math.min(176,w()-76),20).build());manage.active=!SkinClient.busy;addRenderableWidget(Button.builder(Component.literal("Закрыть"),b->onClose()).bounds(x()+w()-70,bottom()-28,70,20).build());
  if(SkinClient.retryable)addRenderableWidget(Button.builder(Component.literal("Повторить"),b->SkinClient.retry()).bounds(x(),bottom()-52,100,20).build()).active=!SkinClient.busy;
 }
 @Override public void tick(){if(ServerMenuClient.previewTransport!=null)return;if(!SkinClient.available()||connection!=minecraft.getConnection()){onClose();return;}if(expected!=null&&!SkinClient.busy&&expected.equals(active()))onClose();}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){super.renderBackground(g,mx,my,d);DialogPanel.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());Ui.status(g,font,SkinClient.busy?"Загрузка…":SkinClient.status,x(),bottom()-72,w(),bottom()-54);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
