package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.chat.Component;
final class ProjectCard extends Button {
 private static final ServerStatusPinger PINGER=new ServerStatusPinger();
 private ServerData data;private long checked;private String address="";
 ProjectCard(int x,int y,Screen parent){super(x,y,156,76,Client.tr("connect"),b->Client.quickConnect(parent),DEFAULT_NARRATION);PINGER.removeAll();}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){var hub=Client.hub;active=hub!=null&&hub.active()!=null;if(!active)return;var server=hub.selectedServer(hub.active());active=server!=null;if(!active)return;
  PINGER.tick();if(!address.equals(server.address())||System.currentTimeMillis()-checked>30000){PINGER.removeAll();checked=System.currentTimeMillis();address=server.address();data=new ServerData(server.name(),address,ServerData.Type.OTHER);var target=data;Client.IO.submit(()->{try{PINGER.pingServer(target,()->{},()->{});}catch(Exception e){target.motd=Client.tr("server.offline");target.ping=-1;}});}
  var font=Minecraft.getInstance().font;g.fill(getX(),getY(),getX()+width,getY()+height,0xD0202020);g.fill(getX(),getY(),getX()+2,getY()+height,0xFFE2BE75);
  g.drawString(font,font.plainSubstrByWidth(hub.menuProjectName(),width-12),getX()+6,getY()+5,0xFFFFFF);Branding.serverIcon(data,g,getX()+6,getY()+19,24);
  int line=0;for(var text:font.split(data.motd==null?Component.empty():data.motd,width-42)){if(line++==2)break;g.drawString(font,text,getX()+34,getY()+19+(line-1)*10,0xBBBBBB);}
  String details=(data.status==null?"…":data.status.getString())+" · "+(data.ping<0?"…":data.ping+" ms");g.drawString(font,font.plainSubstrByWidth(details,width-12),getX()+6,getY()+47,0xBBBBBB);
  String version=(data.version==null?"":data.version.getString())+" · "+hub.menuProjectVersion();g.drawString(font,font.plainSubstrByWidth(version,width-12),getX()+6,getY()+61,0x999999);
  if(isHoveredOrFocused()){g.fill(getX()+6,getY()+19,getX()+30,getY()+43,0xAA101820);g.blitSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace("server_list/join_highlighted"),getX()+6,getY()+19,24,24);}

 }
}
