package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Quick switching closes only on the acknowledged active selection. */
final class QuickSkinsScreen extends ScrollScreen {
 private final Screen parent;private String expected;private Object connection;private boolean loaded;
 QuickSkinsScreen(Screen parent){super(Component.literal("Выберите скин"));this.parent=parent;}
 private int w(){return Math.min(320,width-32);}private int x(){return (width-w())/2;}
 private int columns(){return w()<220?2:3;}
 private int top(){return UiDialog.top(height,Math.min(320,126+((entries().size()+columns())/columns())*68));}
 private int bottom(){return height-top();}
 private JsonArray entries(){return SkinClient.library.has("entries")?SkinClient.library.getAsJsonArray("entries"):new JsonArray();}
 private String active(){return SkinClient.library.has("profile")?Json.opt(SkinClient.library.getAsJsonObject("profile"),"active",""):"";}
 void updated(){if(expected!=null&&!SkinClient.busy&&expected.equals(active())){onClose();return;}rebuildWidgets();}
 @Override protected void init(){if(!loaded){loaded=true;connection=minecraft.getConnection();if(!SkinClient.busy&&ServerMenuClient.previewTransport==null)SkinClient.command("list","",false);}
  int cols=columns(),cw=(Math.max(0,w()-dev.abros.anthub.core.ScrollLayout.TRACK_WIDTH-dev.abros.anthub.core.ScrollLayout.GAP)-(cols-1)*6)/cols;scrollArea((entries().size()+cols)/cols,new dev.abros.anthub.core.NativeLayout.Box(x(),top()+40,Math.max(0,w()),Math.max(0,(bottom()-70)-(top()+40))),68);
  for(int row=firstRow;row<Math.min((entries().size()+cols)/cols,firstRow+visibleRows);row++)for(int col=0;col<cols;col++){
   int n=row*cols+col;if(n>entries().size())break;var entry=n==0?null:entries().get(n-1).getAsJsonObject();String id=entry==null?"":Json.str(entry,"id");
   var card=addRenderableWidget(new UiSkinCard(x()+col*(cw+6),top()+40+(row-firstRow)*68,cw,62,entry,id.equals(active()),id.equals(active()),true,()->{expected=id;SkinClient.command("select",id,false);rebuildWidgets();}));card.active=!SkinClient.busy&&!id.equals(active());
  }
  var manage=addRenderableWidget(UiActions.button(Component.literal("Библиотека скинов"),UiActions.Tone.NORMAL,"",b->SkinsScreen.open(this)).bounds(x(),bottom()-28,w()-UiActions.COMMAND_WIDTH-UiActions.GAP,20).build());manage.active=!SkinClient.busy;
  UiActions.close(new dev.abros.anthub.core.NativeLayout.Box(x(),bottom()-28,w(),20),this::addRenderableWidget,this::onClose);
  if(SkinClient.retryable)UiActions.command(UiActions.Command.RETRY,new dev.abros.anthub.core.NativeLayout.Box(x(),bottom()-52,w(),20),this::addRenderableWidget,SkinClient::retry).active=!SkinClient.busy;
 }
 @Override public void tick(){if(ServerMenuClient.previewTransport!=null)return;if(!SkinClient.available()||connection!=minecraft.getConnection()){onClose();return;}if(expected!=null&&!SkinClient.busy&&expected.equals(active()))onClose();}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());Ui.status(g,font,SkinClient.busy?(expected==null?"Загрузка…":"Применяется…"):SkinClient.status,x(),bottom()-66,w(),bottom()-34);});}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
