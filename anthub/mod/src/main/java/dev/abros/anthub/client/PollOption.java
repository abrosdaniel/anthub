package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
final class PollOption extends Button {
 private final double progress;
 PollOption(int x,int y,int w,String text,double progress,Runnable action){super(x,y,w,20,Component.literal(text),b->action.run(),DEFAULT_NARRATION);this.progress=Math.max(0,Math.min(1,progress));}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float d){int x=getX(),y=getY();g.fill(x,y,x+getWidth(),y+20,0xD0222533);g.fill(x,y,x+(int)(getWidth()*progress),y+20,0xAA645184);if(isHoveredOrFocused()&&active)g.renderOutline(x,y,getWidth(),20,0xFFB49AE8);var font=Minecraft.getInstance().font;g.drawString(font,font.plainSubstrByWidth(getMessage().getString(),getWidth()-12),x+6,y+6,0xEEEEEE);}
}
