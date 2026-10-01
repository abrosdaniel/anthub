package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
final class Ui {
 // Shared text rendering keeps the light theme readable without changing Minecraft screens.
 static int text(GuiGraphics g,Font f,String s,int x,int y,int c){return text(g,f,s,x,y,c,true);}
 static int text(GuiGraphics g,Font f,Component s,int x,int y,int c){return text(g,f,s,x,y,c,true);}
 static int text(GuiGraphics g,Font f,net.minecraft.util.FormattedCharSequence s,int x,int y,int c){return text(g,f,s,x,y,c,true);}
 static int text(GuiGraphics g,Font f,String s,int x,int y,int c,boolean shadow){return g.drawString(f,s,x,y,UiPalette.color(c),false);}
 static int text(GuiGraphics g,Font f,Component s,int x,int y,int c,boolean shadow){return g.drawString(f,s,x,y,UiPalette.color(c),false);}
 static int text(GuiGraphics g,Font f,net.minecraft.util.FormattedCharSequence s,int x,int y,int c,boolean shadow){return g.drawString(f,s,x,y,UiPalette.color(c),false);}
 static void centered(GuiGraphics g,Font f,String s,int x,int y,int c){text(g,f,s,x-f.width(s)/2,y,c);}
 static void centered(GuiGraphics g,Font f,Component s,int x,int y,int c){text(g,f,s,x-f.width(s)/2,y,c);}
 static void centered(GuiGraphics g,Font f,net.minecraft.util.FormattedCharSequence s,int x,int y,int c){text(g,f,s,x-f.width(s)/2,y,c);}
 static void wrap(GuiGraphics g,Font f,net.minecraft.network.chat.FormattedText text,int x,int y,int width,int color){for(var line:f.split(text,width)){Ui.text(g,f,line,x,y,color,false);y+=f.lineHeight;}}
 static void status(GuiGraphics g,Font font,String text,int x,int y,int width,int bottom){
  if(text==null||text.isBlank())return;g.enableScissor(x,y,x+width,bottom);
  for(var line:font.split(Component.literal(text),width)){if(y+font.lineHeight>bottom)break;text(g,font,line,x,y,UiKit.muted(),false);y+=font.lineHeight+2;}g.disableScissor();
 }
}
