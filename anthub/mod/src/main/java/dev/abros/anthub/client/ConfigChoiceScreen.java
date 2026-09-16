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
 private final Screen parent;private final PackOperations operations;private final PackOperations.Review review;
 private final Consumer<Map<String,Boolean>> next;private final Map<String,Boolean> choices=new LinkedHashMap<>();private final List<String> paths;
 private AtomicBoolean cancelled=new AtomicBoolean();private boolean busy;private String status="";
 ConfigChoiceScreen(Screen parent,PackOperations operations,PackOperations.Review review,Consumer<Map<String,Boolean>> next){super(Client.tr("config.choose"));this.parent=parent;this.operations=operations;this.review=review;this.next=next;paths=review.observed().keySet().stream().filter(Planner::configurable).sorted().toList();for(String path:paths)choices.put(path,review.kept().contains(path));}
 protected void init(){int w=Math.min(600,width-30),x=(width-w)/2;scrollArea(paths.size(),48,height-62,48,x+w+3);
  for(int i=firstRow;i<Math.min(paths.size(),firstRow+visibleRows);i++){String path=paths.get(i);int y=48+(i-firstRow)*48;
   boolean enforced=review.release().manifest().files().stream().anyMatch(f->f.path().equals(path)&&f.policy().equals("enforce"));
   var choice=addRenderableWidget(Button.builder(Client.tr(enforced?"config.enforced":choices.get(path)?"config.keep":"config.replace"),b->{choices.put(path,!choices.get(path));rebuildWidgets();}).bounds(x,y+13,w-100,20).build());choice.active=!enforced;
   addRenderableWidget(Button.builder(Client.tr("config.compare"),b->compare(path)).bounds(x+w-96,y+13,96,20).build());
  }
  addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width/2-150,height-26,100,20).build());
  addRenderableWidget(Button.builder(Client.tr("continue"),b->{if(!busy)next.accept(Map.copyOf(choices));}).bounds(width/2-44,height-26,194,20).build());
 }
 private void compare(String path){if(busy)return;busy=true;status=Client.tr("checking").getString();cancelled=new AtomicBoolean();var request=cancelled;operations.compare(review,path,request).whenCompleteAsync((text,error)->{busy=false;if(request.get()||minecraft.screen!=this)return;if(error!=null){status=Errors.message(error instanceof Exception ex?ex:new Exception(error));return;}status="";minecraft.setScreen(new TextScreen(this,Component.literal(path),Client.tr("config.legend").getString()+"\n\n"+text,true));},minecraft);}
 public void render(GuiGraphics g,int x,int y,float dt){super.render(g,x,y,dt);g.drawCenteredString(font,title,width/2,12,0xE2BE75);g.drawCenteredString(font,Client.tr("config.backup"),width/2,28,0xBBBBBB);int w=Math.min(600,width-30);for(int i=firstRow;i<Math.min(paths.size(),firstRow+visibleRows);i++)g.drawString(font,font.plainSubstrByWidth(paths.get(i),w),(width-w)/2,48+(i-firstRow)*48,0xFFFFFF);Ui.status(g,font,status,15,height-56,width-30,height-30);}
 public void onClose(){cancelled.set(true);minecraft.setScreen(parent);}
}
