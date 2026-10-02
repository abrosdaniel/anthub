package dev.abros.anthub.client;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
final class ConfigChoiceScreen extends ScrollScreen {
 private int panelTop(){return UiDialog.top(height,340);}
 private int panelBottom(){return height-panelTop();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(520,width-30),panelTop(),panelBottom());}
 private final Screen parent;private final PackOperations operations;private final PackOperations.Review review;
 private final Consumer<Map<String,Boolean>> next;private final Map<String,Boolean> choices=new LinkedHashMap<>();private final List<String> paths;
 private AtomicBoolean cancelled=new AtomicBoolean();private boolean busy;private String status="";
 ConfigChoiceScreen(Screen parent,PackOperations operations,PackOperations.Review review,Consumer<Map<String,Boolean>> next){super(Client.tr("config.choose"));this.parent=parent;this.operations=operations;this.review=review;this.next=next;paths=review.observed().keySet().stream().filter(Planner::configurable).sorted().toList();for(String path:paths)choices.put(path,review.kept().contains(path));}
 protected void init(){int w=Math.min(520,width-30),x=(width-w)/2;scrollArea(paths.size(),new dev.abros.anthub.core.NativeLayout.Box(x,panelTop()+48,Math.max(0,w),Math.max(0,(panelBottom()-62)-(panelTop()+48))),48);
  for(int i=firstRow;i<Math.min(paths.size(),firstRow+visibleRows);i++){String path=paths.get(i);int y=panelTop()+48+(i-firstRow)*48;
   boolean enforced=review.release().manifest().files().stream().anyMatch(f->f.path().equals(path)&&f.policy().equals("enforce"));
   var choice=addRenderableWidget(UiActions.button(Client.tr(enforced?"config.enforced":choices.get(path)?"config.keep":"config.replace"),UiActions.Tone.NORMAL,"",b->{choices.put(path,!choices.get(path));rebuildWidgets();}).bounds(x,y+13,w-100,20).build());choice.active=!enforced;
   addRenderableWidget(UiActions.button(Client.tr("config.compare"),UiActions.Tone.NORMAL,"",b->compare(path)).bounds(x+w-96,y+13,96,20).build());
  }
  UiActions.close(new dev.abros.anthub.core.NativeLayout.Box(width/2-150,panelBottom()-26,100,20),this::addRenderableWidget,this::onClose);
  addRenderableWidget(UiActions.button(Client.tr("continue"),UiActions.Tone.NORMAL,"",b->{if(!busy)next.accept(Map.copyOf(choices));}).bounds(width/2-44,panelBottom()-26,194,20).build());
 }
 private void compare(String path){if(busy)return;busy=true;status=Client.tr("checking").getString();cancelled=new AtomicBoolean();var request=cancelled;operations.compare(review,path,request).whenCompleteAsync((text,error)->{busy=false;if(request.get()||minecraft.screen!=this)return;if(error!=null){status=Errors.message(error instanceof Exception ex?ex:new Exception(error));return;}status="";minecraft.setScreen(new TextScreen(this,Component.literal(path),Client.tr("config.legend").getString()+"\n\n"+text,true));},minecraft);}
 public void render(GuiGraphics g,int x,int y,float dt){UiDialog.render(parent,this,g,dt,()->{super.render(g,x,y,dt);UiHeading.dialog(g,font,title,(width-Math.min(520,width-30))/2,panelTop(),Math.min(520,width-30));Ui.centered(g,font,Client.tr("config.backup"),width/2,panelTop()+28,UiPalette.color(0xBBBBBB));int w=Math.min(520,width-30);for(int i=firstRow;i<Math.min(paths.size(),firstRow+visibleRows);i++)Ui.text(g,font,font.plainSubstrByWidth(paths.get(i),w),(width-w)/2,panelTop()+48+(i-firstRow)*48,0xFFFFFF);Ui.status(g,font,status,15,panelBottom()-56,width-30,panelBottom()-30);});}
 public void onClose(){cancelled.set(true);minecraft.setScreen(parent);}
}
