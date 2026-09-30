package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
/** Shared surface for secondary screens; content owns its scrolling and input. */
final class DialogPanel {
 private DialogPanel(){}
 static int top(int height,int desired){return Math.max(8,(height-Math.min(desired,height-16))/2);}
 static int bottom(int height,int desired){return height-top(height,desired);}
 static void draw(GuiGraphics g,int width,int contentWidth,int top,int bottom){int left=(width-contentWidth)/2-10;UiTheme.panel(g,left,top,width-left*2,bottom-top,UiPalette.color(0xEF1B252E));}
}
