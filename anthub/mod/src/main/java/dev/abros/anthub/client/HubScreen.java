package dev.abros.anthub.client;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class HubScreen extends ScrollScreen {
 private final java.util.Set<java.util.concurrent.CompletableFuture<?>> reads=new java.util.HashSet<>();
 private <T> java.util.concurrent.CompletableFuture<T> track(java.util.concurrent.CompletableFuture<T> request){reads.add(request);request.whenCompleteAsync((v,e)->reads.remove(request),minecraft);return request;}
 private final Screen parent;private EditBox url;private volatile String status="";private volatile boolean busy;private RepositoryClient.Release release;private boolean initialRequested;private long generation;
 private Button cacheButton,exportButton,addButton;private final Map<String,RepositoryClient.Release> releases=new HashMap<>();private final Set<String> loading=new HashSet<>();private final Set<String> loadAttempted=new HashSet<>();private int leftWidth,rightX,rightWidth,rulesX,paneWidth;private ProjectColumn projectColumn;private final Map<String,ServerData> serverStatuses=new HashMap<>();
 private final ContentPane newsPane=new ContentPane(),rulesPane=new ContentPane();
 private final ServerStatusPinger pinger=new ServerStatusPinger();private long lastPing;
 public HubScreen(Screen parent){super(Client.tr("projects"));this.parent=parent;}
 public HubScreen(Screen parent,RepositoryClient.Release installed){this(parent);release=installed;initialRequested=true;}
 @Override protected void init(){String entered=url==null?"":url.getValue();leftWidth=Math.max(160,Math.min(270,width/3));rightX=leftWidth+20;rightWidth=width-rightX-10;paneWidth=Math.max(30,(rightWidth-10)/2);rulesX=rightX+paneWidth+10;
  url=addRenderableWidget(new EditBox(font,10,32,width-90,20,Client.tr("repository")));url.setMaxLength(2048);url.setValue(entered);url.moveCursorToStart(false);url.setHint(Component.literal("https://github.com/owner/repo"));
  addButton=addRenderableWidget(Button.builder(Client.tr("add.project"),b->fetch(url.getValue(),false,true)).bounds(width-74,32,64,20).build());
  List<String> saved=Client.hub==null?List.of():Client.hub.saved();double offset=projectColumn==null?0:projectColumn.offset;
  projectColumn=addRenderableWidget(new ProjectColumn(10,100,leftWidth,height-164,saved,serverStatuses,()->release==null?"":release.manifest().repository(),repo->select(repo,false),repo->select(repo,true),this::refreshProject,this::buildProject,this::remove));projectColumn.offset=offset;
  addRenderableWidget(Button.builder(Client.tr("registry"),b->catalog()).bounds(10,60,leftWidth-5,20).build());
  int half=(width-24)/2;
  cacheButton=addRenderableWidget(Button.builder(Client.tr("clearcache"),b->clearCache()).bounds(10,height-54,half,20).build());
  exportButton=addRenderableWidget(Button.builder(Client.tr("diagnostics.export"),b->exportDiagnostics()).bounds(14+half,height-54,half,20).build());
  addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width/2-40,height-28,80,20).build());
  newsPane.bounds(rightX,94,paneWidth,Math.max(32,height-158));rulesPane.bounds(rulesX,94,paneWidth,Math.max(32,height-158));
  refreshActions();if(!initialRequested&&Client.hub!=null){initialRequested=true;String repo=Client.hub.selectedRepository();if(repo.isEmpty()&&Client.hub.active()!=null)repo=Client.hub.active().repository();if(!repo.isEmpty())fetch(repo);else{newsPane.text(Client.tr("chooseproject").getString());rulesPane.text(Client.tr("chooseproject").getString());}}
 }
 private void refreshActions(){boolean ready=Client.hub!=null&&!busy&&Client.pending.isEmpty();if(cacheButton!=null)cacheButton.active=ready;if(exportButton!=null)exportButton.active=Client.hub!=null&&!busy;projectColumn.active=ready;addButton.active=Client.hub!=null&&!busy;}
 private void buildProject(String repo){if(busy)return;var found=releases.get(repo);if(found==null){status="Сначала дождитесь загрузки проекта";return;}minecraft.setScreen(new ComponentsScreen(this,found));}
 private void clearCache(){if(busy)return;busy=true;Client.IO.submit(()->{try{status=Client.tr("cache.freed",Client.hub.clearUnusedCache()/1024/1024).getString();}catch(Exception e){status=Errors.message(e);}finally{busy=false;}});}
 private void exportDiagnostics(){if(busy)return;busy=true;Client.IO.submit(()->{try{String report=Diagnostics.report(Client.hub,Client.error);minecraft.execute(()->{if(minecraft.screen!=this)return;minecraft.setScreen(new ReviewScreen(this,Client.tr("diagnostics.preview"),report,Client.tr("diagnostics.save"),()->{minecraft.setScreen(this);Client.IO.submit(()->{try{var path=Diagnostics.save(Client.hub.game,report);status=Client.tr("diagnostics.saved",path.getFileName()).getString();minecraft.execute(()->net.minecraft.Util.getPlatform().openFile(path.getParent().toFile()));}catch(Exception e){status=Errors.message(e);}});},true));});}catch(Exception e){status=Errors.message(e);}finally{busy=false;}});}
 private void fetch(String repo){fetch(repo,false);}
 private RepositoryOperations repositories(){return new RepositoryOperations(Client.hub,Client.NETWORK);}
 private String failureMessage(Throwable failure){while(failure instanceof java.util.concurrent.CompletionException&&failure.getCause()!=null)failure=failure.getCause();return failure instanceof Exception exception?Errors.message(exception):failure.toString();}
 private void fetch(String repo,boolean join){fetch(repo,join,false);}
 private void fetch(String repo,boolean join,boolean refreshDetails){
  if(busy||Client.hub==null)return;busy=true;long request=++generation;status=Client.tr("checking").getString();
  track(repositories().fetch(repo,refreshDetails)).whenCompleteAsync((found,failure)->{
   if(request!=generation||minecraft.screen!=this)return;busy=false;
   if(failure!=null){status=failureMessage(failure);return;}
   try{Client.hub.rememberProject(found.manifest());}catch(Exception e){status=Errors.message(e);return;}
   if(release==null||!release.manifest().repository().equals(found.manifest().repository())){newsPane.text("");rulesPane.text("");}
   release=found;releases.put(found.manifest().repository(),found);status=found.offline()?Client.tr("offline.cached").getString():"";
   loadAttempted.add(found.manifest().repository());Client.syncServers();pingRepository(found.manifest().repository());rebuildWidgets();loadPane(found,"news",newsPane);loadPane(found,"rules",rulesPane);if(join)connect();
  },minecraft);
 }
 private void loadPane(RepositoryClient.Release found,String kind,ContentPane pane){long request=generation;track(repositories().content(found.manifest(),kind)).whenCompleteAsync((text,failure)->{
  if(request!=generation||release!=found)return;
  pane.text(failure!=null?failureMessage(failure):text.isBlank()?Client.tr("content.empty").getString():text);
 },minecraft);}
 private void catalog(){if(Client.hub==null||busy)return;busy=true;long request=generation;track(repositories().catalog()).whenCompleteAsync((repos,failure)->{if(request!=generation||minecraft.screen!=this)return;busy=false;if(failure!=null)status=failureMessage(failure);else minecraft.setScreen(new RegistryScreen(this,repos));},minecraft);}


 private void ping(String repo,Manifest.Server server){var data=new ServerData(server.name(),server.address(),ServerData.Type.OTHER);serverStatuses.put(repo,data);Client.NETWORK.submit(()->{try{pinger.pingServer(data,()->{},()->{});}catch(Exception e){data.motd=Client.tr("server.offline");data.ping=-1;}});}
    public void connectInstalled(RepositoryClient.Release installed){release=installed;busy=false;connect();}
    private void connect(){
        if(release==null||busy||Client.hub==null)return;
        if(!Client.pending.isEmpty()){minecraft.setScreen(new RestartScreen(this,Client.pending));return;}
        busy=true;status=Client.tr("connect.checking").getString();String repository=release.manifest().repository();long request=generation;
        Client.CONNECT.submit(()->{try{
            Client.hub.details.refreshIfStale(repository);var checked=Client.hub.repositories.fetchOrCached(repository);var manifest=checked.manifest();var server=Client.hub.selectedServer(manifest);
            String incompatible=Client.hub.incompatibility(manifest);
            if(!incompatible.isEmpty())throw new IllegalStateException(Client.tr("requires",incompatible).getString());
            if(server==null)throw new IllegalStateException(Client.tr("server.offline").getString());
            var address=net.minecraft.client.multiplayer.resolver.ServerNameResolver.DEFAULT.resolveAddress(ServerAddress.parseString(server.address()));
            if(address.isEmpty())throw new java.io.IOException(Client.tr("connect.unreachable").getString());
            try(var socket=new java.net.Socket()){socket.connect(address.get().asInetSocketAddress(),4000);}catch(java.io.IOException failure){throw new java.io.IOException(Client.tr("connect.unreachable").getString(),failure);}
            boolean update=!Client.hub.activeHash().equals(checked.hash());var issues=update?List.<String>of():Client.hub.audit();
            var foreign=new ArrayList<>(Client.hub.foreignMods());if(Client.loadedJar!=null)foreign.remove(Client.loadedJar.getFileName().toString());
            boolean denied=!foreign.isEmpty()&&manifest.json().getAsJsonObject("policies").get("customFiles").getAsString().equals("deny");
            minecraft.execute(()->{
                if(request!=generation||minecraft.screen!=this)return;busy=false;status="";release=checked;releases.put(repository,checked);
                if(update||!issues.isEmpty()){
                    String description=Client.tr(update?"connect.update":"connect.repair").getString()+"\n"+String.join("\n",issues);
                    minecraft.setScreen(new ReviewScreen(this,Client.tr("connect.readiness"),description,Client.tr(update?"update.pack":"repair"),()->{try{Client.hub.pendingConnection(manifest,server,checked.hash());minecraft.setScreen(new ComponentsScreen(this,checked));}catch(Exception ex){status=Errors.message(ex);minecraft.setScreen(this);}}));return;
                }
                if(denied){minecraft.setScreen(new TextScreen(this,Client.tr("connect.readiness"),Client.tr("connect.foreign",String.join(", ",foreign)).getString()));return;}
                Runnable join=()->{dev.abros.anthub.network.Protocol.expectedServerId=server.id();ConnectScreen.startConnecting(this,minecraft,ServerAddress.parseString(server.address()),new ServerData(server.name(),server.address(),ServerData.Type.OTHER),false,null);};
                if(foreign.isEmpty())join.run();else minecraft.setScreen(new ConfirmScreen(yes->{if(yes)join.run();else minecraft.setScreen(this);},Client.tr("foreign.title"),Client.tr("foreign.message",String.join(", ",foreign))));
            });
        }catch(Exception error){minecraft.execute(()->{if(request!=generation||minecraft.screen!=this)return;busy=false;status=Errors.message(error);Client.error=status;minecraft.setScreen(new ReviewScreen(this,Client.tr("connect.readiness"),status,Client.tr("retry"),()->{minecraft.setScreen(this);connect();}));});}});
    }


 private void select(String repo,boolean join){if(busy)return;var found=releases.get(repo);if(found==null){fetch(repo,join);return;}try{Client.hub.rememberProject(found.manifest());}catch(Exception e){status=Errors.message(e);}if(release!=found){newsPane.text("");rulesPane.text("");}release=found;rebuildWidgets();loadPane(found,"news",newsPane);loadPane(found,"rules",rulesPane);if(join)connect();}
 private void refreshProject(String repo){if(loading.add(repo))loadProject(repo,true);}
 private void loadProject(String repo,boolean refreshDetails){loadAttempted.add(repo);track(repositories().fetch(repo,refreshDetails)).whenCompleteAsync((found,failure)->{
  loading.remove(repo);if(!Client.hub.saved().contains(repo))return;
  if(failure!=null){status=failureMessage(failure);return;}
  releases.put(repo,found);Client.syncServers();if(minecraft.screen==this){pingRepository(repo);if(release!=null&&release.manifest().repository().equals(repo)){release=found;loadPane(found,"news",newsPane);loadPane(found,"rules",rulesPane);}}
 },minecraft);}
 private void pingRepository(String repo){var found=releases.get(repo);if(found==null)return;var server=Client.hub.selectedServer(found.manifest());if(server!=null)ping(repo,server);}
 private void remove(String repo){boolean active=Client.hub.active()!=null&&Client.hub.active().repository().equals(repo);minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(!yes)return;if(active){busy=true;Client.IO.submit(()->{try{String id=Client.hub.deactivate();Client.hub.removeAfterDeactivation(repo);Client.pending=id;minecraft.execute(()->minecraft.setScreen(new RestartScreen(this,id)));}catch(Exception e){status=Errors.message(e);}finally{busy=false;}});}else try{Client.hub.removeRepository(repo);releases.remove(repo);serverStatuses.remove(repo);if(release!=null&&release.manifest().repository().equals(repo)){release=null;newsPane.text("");rulesPane.text("");}rebuildWidgets();}catch(Exception e){status=Errors.message(e);}},Client.tr("remove"),Client.tr(active?"remove.active":"remove.confirm")));}

 @Override public void tick(){super.tick();refreshActions();pinger.tick();if(Client.hub!=null)for(String repo:Client.hub.saved())if(!busy&&!loadAttempted.contains(repo)&&loading.add(repo))loadProject(repo,false);if(release!=null&&System.currentTimeMillis()-lastPing>30000){lastPing=System.currentTimeMillis();pinger.removeAll();for(String repo:Client.hub.saved())pingRepository(repo);}}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(projectColumn!=null&&projectColumn.mouseScrolled(x,y,dx,dy))return true;if(newsPane.scroll(x,y,dy)||rulesPane.scroll(x,y,dy))return true;return x<=10+leftWidth&&super.mouseScrolled(x,y,dx,dy);}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==0){if(newsPane.click(x,y)||rulesPane.click(x,y))return true;for(var pane:List.of(newsPane,rulesPane)){var style=pane.link(font,x,y);if(style!=null&&style.getClickEvent()!=null)return handleComponentClicked(style);}}return super.mouseClicked(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&(newsPane.drag(y)||rulesPane.drag(y)))return true;return super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){newsPane.release();rulesPane.release();return super.mouseReleased(x,y,button);}
 @Override public void render(GuiGraphics g,int x,int y,float delta){super.render(g,x,y,delta);g.drawCenteredString(font,title,width/2,12,0xE2BE75);g.drawString(font,Client.tr("saved.projects"),10,86,0xBBBBBB);
  if(release!=null){var manifest=release.manifest();g.drawString(font,font.plainSubstrByWidth(Client.hub.projectName(manifest)+" · "+manifest.version(),rightWidth),rightX,60,Branding.accent(manifest));String state=Client.tr(Client.hub.activeHash().equals(release.hash())?"project.active":Client.hub.active()!=null&&Client.hub.active().repository().equals(manifest.repository())?"update.available":"project.savedonly").getString();String incompatible=Client.hub.incompatibility(manifest);if(!incompatible.isEmpty())state=Client.tr("requires",incompatible).getString();var server=Client.hub.selectedServer(manifest);if(server!=null)state+=" · "+server.name();g.drawString(font,font.plainSubstrByWidth(state,rightWidth),rightX,72,0xBBBBBB);}
  g.drawString(font,Client.tr("news"),rightX,84,0xE2BE75);g.drawString(font,Client.tr("rules"),rulesX,84,0xE2BE75);newsPane.render(g,font);rulesPane.render(g,font);
  Ui.status(g,font,status,10,height-78,width-20,height-58);
 }
 @Override public void removed(){generation++;for(var read:reads)read.cancel(true);reads.clear();loading.clear();loadAttempted.clear();busy=false;pinger.removeAll();initialRequested=false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
