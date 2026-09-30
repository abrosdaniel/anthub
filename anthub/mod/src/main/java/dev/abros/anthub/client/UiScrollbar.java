package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
/** Same thumb geometry and colours for independent lists and scrolling screens. */
final class UiScrollbar {
 private UiScrollbar(){}
 static int thumb(int top,int bottom,int visible,int count){int track=Math.max(0,bottom-top);return Math.min(track,Math.max(12,track*visible/Math.max(1,count)));}
 static void draw(GuiGraphics g,int right,int top,int bottom,int visible,int count,double position){if(count<=visible||bottom<=top)return;int h=thumb(top,bottom,visible,count);int y=top+(int)((bottom-top-h)*Math.max(0,Math.min(position,count-visible))/(count-visible));g.fill(right,top,right+6,bottom,UiPalette.color(0xFF101820));g.fill(right,y,right+6,y+h,UiPalette.color(0xFF738899));}
}
