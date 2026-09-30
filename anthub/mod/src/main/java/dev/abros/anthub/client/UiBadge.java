package dev.abros.anthub.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
/** A compact, non-interactive status label shared by cards and detail headers. */
final class UiBadge {
 private UiBadge(){}
 static int draw(GuiGraphics g,Font font,String label,int x,int y,int maxWidth,int accent){if(label.isBlank()||maxWidth<12)return 0;String text=UiKit.fit(font,label,maxWidth-10);int w=Math.min(maxWidth,font.width(text)+10);UiKit.surface(g,x,y,w,14,UiTheme.mix(UiPalette.color(0xFF21313E),accent,0.15f));g.drawString(font,text,x+5,y+3,accent,false);return w;}
}
