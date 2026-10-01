package dev.abros.anthub.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
/** Context and a next step, without an inactive giant button. */
final class UiEmptyState {
 private UiEmptyState(){}
 static void draw(GuiGraphics g,Font font,String title,String hint,int x,int y,int width,int bottom){if(width<24||bottom-y<24)return;g.fill(x,y,x+2,Math.min(bottom,y+44),UiKit.accent());Ui.text(g,font,UiKit.fit(font,title,width-14),x+10,y+2,UiKit.text(),false);int lineY=y+18;for(var line:font.split(net.minecraft.network.chat.Component.literal(hint),width-14)){if(lineY+9>bottom)break;Ui.text(g,font,line,x+10,lineY,UiKit.muted(),false);lineY+=12;}}
}
