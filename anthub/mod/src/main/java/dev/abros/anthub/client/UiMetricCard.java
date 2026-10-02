package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
/** Read-only dashboard measure, visually distinct from navigation cards. */
final class UiMetricCard extends net.minecraft.client.gui.components.Button {
 private final String label,value;
 UiMetricCard(int x,int y,int width,String label,String value){this(x,y,width,label,value,null);}
 UiMetricCard(int x,int y,int width,String label,String value,Runnable action){super(x,y,width,56,Component.literal(label+": "+value),b->{if(action!=null)action.run();},DEFAULT_NARRATION);this.label=label;this.value=value;active=action!=null;}
 @Override public void updateWidgetNarration(NarrationElementOutput output){output.add(NarratedElementType.TITLE,getMessage());}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){int x=getX(),y=getY(),w=getWidth();var font=Minecraft.getInstance().font;UiKit.surface(g,x,y,w,56,isHoveredOrFocused()&&active?UiPalette.color(0xFF304650):UiKit.surface());UiKit.detail(g,x,y,w,56);Ui.text(g,font,UiKit.fit(font,label,w-16),x+8,y+9,UiKit.muted(),false);Ui.text(g,font,value,x+8,y+30,UiKit.accent(),false);}
}
