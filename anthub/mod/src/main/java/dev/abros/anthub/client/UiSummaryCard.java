package dev.abros.anthub.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A summary and its destination form one keyboard-accessible target. */
final class UiSummaryCard extends Button {
 private final String title,value;private final List<String> details;private final int accent;
 UiSummaryCard(int x,int y,int width,String title,String value,List<String> details,int accent,Runnable action){super(x,y,width,88,Component.literal(title+". "+value+". "+String.join(". ",details)),b->action.run(),DEFAULT_NARRATION);this.title=title;this.value=value;this.details=List.copyOf(details);this.accent=accent;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
  int x=getX(),y=getY(),w=getWidth(),color=UiPalette.color(accent);var font=Minecraft.getInstance().font;
  UiKit.surface(g,x,y,w,88,UiTheme.mix(UiKit.surface(),UiPalette.color(0xFF314350),UiTheme.hover(this)*0.55f));
  g.fill(x,y,x+3,y+88,color);if(isHoveredOrFocused())g.renderOutline(x,y,w,88,color);
  g.enableScissor(x+8,y+5,x+w-8,y+83);
  g.drawString(font,UiKit.fit(font,title,w-40),x+12,y+10,UiKit.muted(),false);
  g.drawString(font,UiKit.fit(font,value,w-26),x+12,y+29,color,false);
  for(int i=0;i<Math.min(2,details.size());i++)g.drawString(font,UiKit.fit(font,details.get(i),w-26),x+12,y+52+i*14,UiKit.text(),false);
  UiIcons.draw(g,UiIcons.RIGHT,x+w-23,y+9,color);g.disableScissor();
 }
}
