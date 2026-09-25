package dev.abros.anthub.client;
import dev.abros.anthub.core.Manifest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
public final class ConnectionCountdown extends Screen {
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){super.renderBackground(g,x,y,d);DialogPanel.draw(g,width,Math.min(300,width-24),height/2-60,height/2+56);}
 private final Screen parent;private final Manifest.Server server;private int ticks=100;
 public ConnectionCountdown(Screen parent,Manifest.Server server){super(Client.tr("connect"));this.parent=parent;this.server=server;}
 protected void init(){addRenderableWidget(Button.builder(Client.tr("cancel"),b->onClose()).bounds(width/2-100,height/2+20,200,20).build());}
 public void tick(){if(--ticks<=0){dev.abros.anthub.network.Protocol.expectedServerId=server.id();ConnectScreen.startConnecting(parent,minecraft,ServerAddress.parseString(server.address()),new ServerData(server.name(),server.address(),ServerData.Type.OTHER),false,null);}}
 public void render(GuiGraphics g,int x,int y,float pt){super.render(g,x,y,pt);g.drawWordWrap(font,Client.tr("connecting",server.name(),Math.max(0,(ticks+19)/20)),width/2-150,height/2-40,300,0xFFFFFF);}
 public void onClose(){minecraft.setScreen(parent);}
}
