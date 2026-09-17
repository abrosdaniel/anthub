package dev.abros.anthub.client;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class RegistryScreen extends ScrollScreen {
 private final Screen parent;
 private final List<String> repositories;
 private final Map<String,RepositoryClient.Release> projects=new HashMap<>();
 private final Map<String,String> errors=new HashMap<>();
 private final Map<String,CompletableFuture<RepositoryClient.Release>> requests=new HashMap<>();
 private final Set<String> saving=new HashSet<>();
 private String status="";private long generation;
 public RegistryScreen(Screen parent,List<String> repositories){super(Client.tr("registry"));this.parent=parent;this.repositories=repositories.stream().distinct().toList();}
 @Override protected void init(){
  int w=Math.min(440,width-40),x=(width-w)/2;scrollArea(repositories.size(),38,height-65,54,x+w+4);
  for(int i=firstRow;i<Math.min(repositories.size(),firstRow+visibleRows);i++){
   String repo=repositories.get(i);boolean saved=Client.hub.saved().contains(repo);var project=projects.get(repo);int y=38+(i-firstRow)*54;
   String label=saved?"Добавлен":saving.contains(repo)?"Сохранение…":errors.containsKey(repo)?"Повторить":project==null?"Загрузка…":"Добавить";
   var button=addRenderableWidget(Button.builder(Component.literal(label),b->{if(errors.remove(repo)!=null){fetch(repo);rebuildRows();}else save(repo);}).bounds(x+w-96,y+15,90,20).build());
   button.active=!saved&&!saving.contains(repo)&&(project!=null||errors.containsKey(repo));
   if(project==null&&!errors.containsKey(repo))fetch(repo);
  }
  addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width/2-100,height-26,200,20).build());
 }
 private void rebuildRows(){int focus=children().indexOf(getFocused());rebuildWidgets();if(focus>=0&&focus<children().size())setFocused(children().get(focus));}
 private void fetch(String repo){
  if(requests.containsKey(repo))return;long epoch=generation;
  var future=new RepositoryOperations(Client.hub,Client.NETWORK).fetch(repo);requests.put(repo,future);
  future.whenCompleteAsync((found,error)->{if(epoch!=generation)return;requests.remove(repo);if(error==null)projects.put(repo,found);else errors.put(repo,message(error));if(minecraft.screen==this)rebuildRows();},minecraft);
 }
 private static String message(Throwable error){while(error.getCause()!=null&&(error instanceof java.util.concurrent.CompletionException||error instanceof java.util.concurrent.ExecutionException))error=error.getCause();return error instanceof Exception e?Errors.message(e):"Не удалось загрузить проект";}
 private void save(String repo){
  var found=projects.get(repo);if(found==null||!saving.add(repo))return;rebuildRows();
  Client.IO.submit(()->{String failure="";try{Client.hub.rememberProject(found.manifest());}catch(Exception e){failure=Errors.message(e);}String result=failure;minecraft.execute(()->{saving.remove(repo);status=result;if(minecraft.screen==this)rebuildRows();});});
 }
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float pt){
  super.renderBackground(g,mx,my,pt);int w=Math.min(440,width-40),x=(width-w)/2;
  for(int i=firstRow;i<Math.min(repositories.size(),firstRow+visibleRows);i++){
   var repo=repositories.get(i);var found=projects.get(repo);int y=38+(i-firstRow)*54;
   g.fill(x,y,x+w,y+48,0xDC1B252E);
   g.drawString(font,font.plainSubstrByWidth(found==null?Client.hub.projectLabel(repo):found.manifest().name(),w-110),x+8,y+8,0xE2BE75);
   g.drawString(font,font.plainSubstrByWidth(repo.replace("https://github.com/",""),w-110),x+8,y+23,0xA9B9C8);
   String detail=errors.getOrDefault(repo,found==null?"Загрузка…":found.manifest().version());
   g.drawString(font,font.plainSubstrByWidth(detail,w-110),x+8,y+35,errors.containsKey(repo)?0xFF8888:0x879BAD);
  }
 }
 @Override public void render(GuiGraphics g,int mx,int my,float pt){super.render(g,mx,my,pt);g.drawCenteredString(font,title,width/2,12,0xE2BE75);Ui.status(g,font,status,10,height-60,width-20,height-34);if(repositories.isEmpty())g.drawCenteredString(font,Client.tr("registry.empty"),width/2,50,0xBBBBBB);}
 @Override public void removed(){generation++;for(var future:requests.values())future.cancel(true);requests.clear();}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
