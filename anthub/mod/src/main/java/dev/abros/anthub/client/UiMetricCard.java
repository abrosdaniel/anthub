package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
/** Read-only dashboard measure, visually distinct from navigation cards. */
final class UiMetricCard extends AbstractWidget {
 private final String label,value;
 UiMetricCard(int x,int y,int width,String label,String value){super(x,y,width,56,Component.literal(label+": "+value));this.label=label;this.value=value;active=false;}
 @Override protected void updateWidgetNarration(NarrationElementOutput output){output.add(NarratedElementType.TITLE,getMessage());}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){int x=getX(),y=getY(),w=getWidth();var font=Minecraft.getInstance().font;UiKit.surface(g,x,y,w,56,UiKit.surface());g.drawString(font,UiKit.fit(font,label,w-16),x+8,y+9,UiKit.muted(),false);g.drawString(font,value,x+8,y+30,UiKit.accent(),false);}
}
