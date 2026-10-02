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
 private static int decorative(int color){int alpha=color>>>24;float strength=AccessibilityScreen.decoration()==2?1.7f:1f;return (color&0x00FFFFFF)|(Math.min(255,Math.round(alpha*strength))<<24);}
 static void material(GuiGraphics g,int x,int y,int w,int h){
  surface(g,x,y,w,h,AccessibilityScreen.background(surface()));
  boolean decorate=AccessibilityScreen.decoration()>0;
  int subtle=UiPalette.light()?decorative(0x087E3D55):decorative(0x08FFFFFF);
  String theme=decorate?UiPalette.id():"";
  if(theme.equals("create-stuff")){for(int yy=y+10;yy<y+h-3;yy+=14)g.fill(x+3,yy,x+w-3,yy+1,subtle);}
  else if(theme.equals("mine-main")){for(int yy=y+12;yy<y+h-3;yy+=18){g.fill(x+3,yy,x+w-3,yy+1,subtle);for(int xx=x+18;xx<x+w-3;xx+=36)g.fill(xx,yy-9,xx+1,yy,subtle);}}
  else if(theme.equals("midnight-blue")){g.fillGradient(x+3,y+3,x+w-3,y+Math.min(h-3,36),decorative(0x1291B8D1),decorative(0x0091B8D1));}
  else if(theme.equals("moss-stone")){for(int yy=y+18;yy<y+h-3;yy+=24)g.fill(x+3,yy,x+w-3,yy+1,decorative(0x06A8BF93));}
  else if(theme.equals("copper-ember")){g.fill(x+3,y+2,x+w-3,y+3,decorative(0x12C99878));}
  else if(theme.equals("obsidian")){g.fillGradient(x+3,y+3,x+w-3,y+Math.min(h-3,36),decorative(0x14978BDD),decorative(0x00978BDD));}
  if(theme.equals("silver-slate"))g.fill(x+3,y+3,x+w-3,y+4,decorative(0x12BAC7D2));
  else if(theme.equals("deep-teal"))g.fillGradient(x+3,y+3,x+w-3,y+Math.min(h-3,28),decorative(0x1286C4B8),decorative(0x0086C4B8));
  else if(theme.equals("walnut-workshop")){for(int yy=y+12;yy<y+h-3;yy+=16)g.fill(x+3,yy,x+w-3,yy+1,decorative(0x07CBB393));}
  else if(theme.equals("berry-night"))g.fillGradient(x+3,y+3,x+w-3,y+Math.min(h-3,28),decorative(0x10B6A3C7),decorative(0x00B6A3C7));
  if(theme.equals("golden-dark")){for(int xx:new int[]{x+6,x+w-8}){g.fill(xx,y+6,xx+2,y+8,UiPalette.color(decorative(0x806D5838)));g.fill(xx,y+h-8,xx+2,y+h-6,UiPalette.color(decorative(0x806D5838)));}}
  g.fill(x+3,y,x+w-3,y+1,UiPalette.color(0xFF526674));
  g.fill(x+3,y+h-1,x+w-3,y+h,UiPalette.color(0xFF13202A));
 }
 static void detail(GuiGraphics g,int x,int y,int w,int h){if(w<60||h<32||AccessibilityScreen.decoration()==0)return;String theme=UiPalette.id();int edge=UiTheme.mix(UiKit.surface(),UiKit.accent(),AccessibilityScreen.decoration()==2?0.20f:0.10f);
  switch(theme){
   case "golden-dark","copper-ember"->{for(int px:new int[]{x+5,x+w-7})g.fill(px,y+5,px+2,y+7,edge);}
   case "create-stuff","walnut-workshop"->{for(int yy=y+14;yy<y+h-4;yy+=18)g.fill(x+4,yy,x+w-4,yy+1,decorative(0x05D3B18A));g.fill(x+w-18,y+5,x+w-5,y+6,edge);}
   case "obsidian","midnight-blue","deep-teal","berry-night"->g.fillGradient(x+3,y+3,x+w-3,y+Math.min(28,h-3),decorative(0x089C91D3),decorative(0x009C91D3));
   case "mine-main","moss-stone"->{for(int xx=x+16;xx<x+w-4;xx+=40)g.fill(xx,y+3,xx+1,y+6,edge);}
   case "love-pink"->{g.fill(x+5,y+3,x+w-5,y+4,decorative(0x08FFFFFF));}
   default->g.fill(x+4,y+3,x+w-4,y+4,decorative(0x08FFFFFF));
  }
 }
 /** A quiet raised surface: depth comes from edges, not bright full-width rules. */
 static void plate(GuiGraphics g,int x,int y,int w,int h,int color){
  if(w<4||h<4)return;
  surface(g,x,y+1,w,h,UiPalette.light()?0x167E3D55:0x30000000);
  surface(g,x,y,w,h,color);
  int edge=UiTheme.mix(color,UiPalette.light()?0xFF583744:0xFFD7E2EC,UiPalette.light()?0.10f:0.07f);
  g.fill(x+2,y,x+w-2,y+1,edge);
 }
 static void checkbox(GuiGraphics g,int x,int y,boolean checked,int color){
  g.fill(x,y,x+CHECK_SIZE,y+CHECK_SIZE,UiPalette.color(0xFF13202A));
  g.renderOutline(x,y,CHECK_SIZE,CHECK_SIZE,color);
  if(checked)UiIcons.draw(g,UiIcons.CHECK,x-1,y-1,color);
 }
 static String fit(net.minecraft.client.gui.Font font,String text,int width){if(text.isEmpty()||font.width(text)<=width)return text;return font.plainSubstrByWidth(text,Math.max(1,width-font.width("…")))+"…";}
}
