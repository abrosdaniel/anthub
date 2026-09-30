package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
/** Shared geometry and state marks. Screens arrange components; components own their appearance. */
final class UiKit {
 static final int ACCENT=0xFF83C6C4;
 static final int CONTROL_HEIGHT=20, ROW_STRIDE=24, GAP=4, INSET=8, CHECK_SIZE=10;
 private UiKit(){}
 static int accent(){return UiPalette.color(ACCENT);}
 static int text(){return AccessibilityScreen.foreground(UiPalette.color(0xFFE0E9EE));}
 static int muted(){return UiPalette.color(0xFFA4B5C0);}
 static int surface(){return UiPalette.color(0xFF202F39);}
 /** Two-pixel corners retain Minecraft's crisp pixel grid while separating surfaces. */
 static void surface(GuiGraphics g,int x,int y,int w,int h,int color){if(w<4||h<4)return;g.fill(x+2,y,x+w-2,y+h,color);g.fill(x,y+2,x+2,y+h-2,color);g.fill(x+w-2,y+2,x+w,y+h-2,color);}
 static void checkbox(GuiGraphics g,int x,int y,boolean checked,int color){
  g.fill(x,y,x+CHECK_SIZE,y+CHECK_SIZE,UiPalette.color(0xFF13202A));
  g.renderOutline(x,y,CHECK_SIZE,CHECK_SIZE,color);
  if(checked)UiIcons.draw(g,UiIcons.CHECK,x-1,y-1,color);
 }
 static String fit(net.minecraft.client.gui.Font font,String text,int width){if(font.width(text)<=width)return text;return font.plainSubstrByWidth(text,Math.max(1,width-font.width("…")))+"…";}
}
