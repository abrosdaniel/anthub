package dev.abros.anthub.client;

import com.google.gson.JsonObject;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;

/** The server can propose a repository; only the player's action starts a fetch/install review. */
final class ServerUpdateScreen extends Screen {
    private final Screen parent;private final JsonObject offer;private final ServerData server;
    private String status="";private boolean busy;
    ServerUpdateScreen(Screen parent,JsonObject offer,ServerData server){super(Client.tr("server.update"));this.parent=parent;this.offer=offer;this.server=server;}
    @Override protected void init(){
        addRenderableWidget(Button.builder(Client.tr("server.checkupdate"),b->{if(busy)return;busy=true;b.active=false;status=Client.tr("checking").getString();
            new RepositoryOperations(Client.hub,Client.IO).fetch(Json.str(offer,"repository")).whenCompleteAsync((release,error)->{
                busy=false;if(minecraft.screen!=this)return;b.active=true;
                if(error!=null){status=error.getCause()==null?error.getMessage():error.getCause().getMessage();return;}
                try{
                    if(!Hashes.sha256(release.bytes()).equals(Json.str(offer,"requiredLockSha256")))throw new IllegalArgumentException(Client.tr("server.versionunavailable").getString());
                    var target=release.manifest().servers().getFirst();
                    Client.hub.rememberProject(release.manifest());Client.hub.pendingConnection(release.manifest(),target,Hashes.sha256(release.bytes()));
                    minecraft.setScreen(new ComponentsScreen(this,release));
                }catch(Exception ex){status=Errors.message(ex);}
            },minecraft);
        }).bounds(width/2-120,height-54,240,20).build());
        addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(width/2-100,height-28,200,20).build());
    }
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);g.drawCenteredString(font,title,width/2,20,0xE2BE75);
        Ui.status(g,font,server.name+"\n"+Client.tr("server.required",Json.opt(offer,"requiredVersion","?")).getString()+"\n"+Json.opt(offer,"repository","")+"\n\n"+status,20,55,width-40,height-65);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
}
