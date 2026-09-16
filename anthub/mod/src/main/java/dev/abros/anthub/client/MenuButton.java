package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** One entry point shared by title and pause menus. */
final class MenuButton extends Button {
 MenuButton(int x,int y,Screen parent){super(x,y,104,24,Component.literal("AntHub"),b->{var mc=Minecraft.getInstance();mc.setScreen(Client.pending.isEmpty()?new HubScreen(parent):new RestartScreen(parent,Client.pending));},DEFAULT_NARRATION);}
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float delta){
  super.renderWidget(g,x,y,delta);Branding.icon(g,getX()+5,getY()+4,16);
  if(!Client.pending.isEmpty())g.drawString(Minecraft.getInstance().font,"!",getX()+getWidth()-12,getY()+8,0xFFE2BE75);
  setTooltip(Client.pending.isEmpty()?null:Tooltip.create(Client.tr("restart.title")));
 }
}
