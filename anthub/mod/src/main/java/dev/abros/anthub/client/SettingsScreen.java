package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
public final class SettingsScreen extends Screen {
    private final Screen parent;private final String repository;private volatile String status="";private volatile boolean busy;
    public SettingsScreen(Screen parent,String repository){super(Component.literal(Client.tr("settings").getString()+" · "+Client.hub.projectLabel(repository)));this.parent=parent;this.repository=repository;}
    @Override protected void init(){int y=36;
        addRenderableWidget(Button.builder(Client.tr("rollback"),b->reviewRollback()).bounds(width/2-130,y,260,20).build());
        addRenderableWidget(Button.builder(Client.tr("clearcache"),b->{if(busy||Client.hub==null)return;busy=true;Client.IO.submit(()->{try{status=Client.tr("cache.freed",Client.hub.clearUnusedCache()/1024/1024).getString();}catch(Exception e){status=Errors.message(e);}finally{busy=false;}});}).bounds(width/2-130,y+26,260,20).build());
        addRenderableWidget(Button.builder(Client.tr("deactivate"),b->minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes&&!busy){busy=true;Client.IO.submit(()->{try{String id=Client.hub.deactivate();Client.pending=id;minecraft.execute(()->minecraft.setScreen(new RestartScreen(this,id)));}catch(Exception e){status=Errors.message(e);}finally{busy=false;}});}},Client.tr("deactivate"),Client.tr("deactivate.confirm")))).bounds(width/2-130,y+52,260,20).build());
        addRenderableWidget(Button.builder(Client.tr("history"),b->{if(busy||Client.hub==null)return;busy=true;Client.IO.submit(()->{try{var releases=Client.hub.history(repository);var entries=dev.abros.anthub.core.InstallationHistory.read(Client.hub.game,repository);String newest;try{newest=Client.hub.repositories.fetch(repository).manifest().version();}catch(Exception unavailable){newest="";}final String latest=newest;minecraft.execute(()->{if(minecraft.screen==this)minecraft.setScreen(new HistoryScreen(this,releases,entries,latest));});}catch(Exception e){status=Errors.message(e);}finally{busy=false;}});}).bounds(width/2-130,y+78,260,20).build());
        addRenderableWidget(Button.builder(Client.tr("diagnostics.export"),b->{if(busy)return;busy=true;Client.IO.submit(()->{try{String report=dev.abros.anthub.core.Diagnostics.report(Client.hub,Client.error);minecraft.execute(()->{if(minecraft.screen!=this)return;minecraft.setScreen(new ReviewScreen(this,Client.tr("diagnostics.preview"),report,Client.tr("diagnostics.save"),()->{minecraft.setScreen(this);Client.IO.submit(()->{try{var path=dev.abros.anthub.core.Diagnostics.save(Client.hub.game,report);status=Client.tr("diagnostics.saved",path.getFileName()).getString();minecraft.execute(()->net.minecraft.Util.getPlatform().openFile(path.getParent().toFile()));}catch(Exception ex){status=Errors.message(ex);}});},true));});}catch(Exception ex){status=Errors.message(ex);}finally{busy=false;}});}).bounds(width/2-130,y+104,260,20).build());
        addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width/2-100,height-26,200,20).build());
    }
    private void reviewRollback(){
        if(busy)return;busy=true;Client.IO.submit(()->{try{
            var state=Client.hub.state();if(!state.has("transactionId"))throw new java.io.IOException(Client.tr("history.empty").getString());
            var dir=new dev.abros.anthub.core.Transactions(Client.hub.game).directory(state.get("transactionId").getAsString());
            var previous=dev.abros.anthub.core.Json.read(dir.resolve("previous-state.json"));
            String version="—",compatibility="";if(previous.has("lock")){var target=dev.abros.anthub.core.Manifest.parse(previous.getAsJsonObject("lock"));version=target.version();compatibility=Client.hub.incompatibility(target);}
            String latest;try{latest=Client.hub.repositories.fetch(repository).manifest().version();}catch(Exception offline){latest="";}
            String text=Client.tr("rollback.confirm").getString()+"\n\n"+Client.tr("rollback.target",version).getString()+"\n"+Client.tr(latest.isEmpty()?"history.server.unknown":latest.equals(version)?"history.server.latest":"history.server.old",latest).getString();
            if(!compatibility.isEmpty())text+="\n"+Client.tr("requires",compatibility).getString();final String body=text;final boolean allowed=compatibility.isEmpty();
            minecraft.execute(()->{if(minecraft.screen!=this)return;if(!allowed){minecraft.setScreen(new TextScreen(this,Client.tr("rollback"),body));return;}minecraft.setScreen(new ReviewScreen(this,Client.tr("rollback"),body,Client.tr("rollback"),()->{minecraft.setScreen(this);if(busy)return;busy=true;Client.IO.submit(()->{try{String id=Client.hub.rollback();Client.pending=id;minecraft.execute(()->{if(minecraft.screen==this)minecraft.setScreen(new RestartScreen(this,id));});}catch(Exception ex){status=Errors.message(ex);}finally{busy=false;}});}));});
        }catch(Exception ex){status=Errors.message(ex);}finally{busy=false;}});
    }
    @Override public void tick(){super.tick();for(var child:children())if(child instanceof Button b&&!b.getMessage().equals(Client.tr("back"))){boolean active=Client.hub!=null&&!busy&&Client.pending.isEmpty();if(b.getMessage().equals(Client.tr("rollback"))||b.getMessage().equals(Client.tr("history"))||b.getMessage().equals(Client.tr("deactivate")))active=active&&(b.getMessage().equals(Client.tr("history"))||Client.hub.active()!=null&&Client.hub.active().repository().equals(repository));b.active=active;}}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){super.render(g,mx,my,pt);g.drawCenteredString(font,title,width/2,12,0xE2BE75);Ui.status(g,font,status,Math.max(10,width/2-130),174,Math.min(260,width-20),height-32);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
