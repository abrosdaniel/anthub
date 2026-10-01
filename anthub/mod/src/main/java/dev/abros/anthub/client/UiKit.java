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

 /** Restrained original materials, drawn only inside AntHub panels. */
 static void material(GuiGraphics g,int x,int y,int w,int h){
  surface(g,x,y,w,h,AccessibilityScreen.background(surface()));
  int subtle=UiPalette.light()?0x087E3D55:0x08FFFFFF;
  String theme=UiPalette.id();
  if(theme.equals("create-stuff")){for(int yy=y+10;yy<y+h-3;yy+=14)g.fill(x+3,yy,x+w-3,yy+1,subtle);}
  else if(theme.equals("mine-main")){for(int yy=y+12;yy<y+h-3;yy+=18){g.fill(x+3,yy,x+w-3,yy+1,subtle);for(int xx=x+18;xx<x+w-3;xx+=36)g.fill(xx,yy-9,xx+1,yy,subtle);}}
  else if(theme.equals("obsidian")){g.fillGradient(x+3,y+3,x+w-3,y+Math.min(h-3,36),0x14978BDD,0x00978BDD);}
  if(theme.equals("golden-dark")){for(int xx:new int[]{x+6,x+w-8}){g.fill(xx,y+6,xx+2,y+8,UiPalette.color(0x806D5838));g.fill(xx,y+h-8,xx+2,y+h-6,UiPalette.color(0x806D5838));}}
  g.fill(x+3,y,x+w-3,y+1,UiPalette.color(0xFF526674));
  g.fill(x+3,y+h-1,x+w-3,y+h,UiPalette.color(0xFF13202A));
 }
 /** A quiet raised surface: depth comes from edges, not bright full-width rules. */
 static void plate(GuiGraphics g,int x,int y,int w,int h,int color){
  if(w<4||h<4)return;
  surface(g,x,y+1,w,h,UiPalette.light()?0x167E3D55:0x30000000);
  surface(g,x,y,w,h,color);
  int edge=UiTheme.mix(color,UiPalette.light()?0xFF684252:0xFFD7E2EC,UiPalette.light()?0.10f:0.07f);
  g.fill(x+2,y,x+w-2,y+1,edge);
 }
 static void checkbox(GuiGraphics g,int x,int y,boolean checked,int color){
  g.fill(x,y,x+CHECK_SIZE,y+CHECK_SIZE,UiPalette.color(0xFF13202A));
  g.renderOutline(x,y,CHECK_SIZE,CHECK_SIZE,color);
  if(checked)UiIcons.draw(g,UiIcons.CHECK,x-1,y-1,color);
 }
 static String fit(net.minecraft.client.gui.Font font,String text,int width){if(text.isEmpty()||font.width(text)<=width)return text;return font.plainSubstrByWidth(text,Math.max(1,width-font.width("…")))+"…";}
}
