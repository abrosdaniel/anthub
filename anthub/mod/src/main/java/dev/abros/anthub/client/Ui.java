package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
final class Ui {
 static void status(GuiGraphics g,Font font,String text,int x,int y,int width,int bottom){
  if(text==null||text.isBlank())return;g.enableScissor(x,y,x+width,bottom);
  for(var line:font.split(Component.literal(text),width)){if(y+font.lineHeight>bottom)break;g.drawString(font,line,x,y,AccessibilityScreen.foreground(0xCCCCCC));y+=font.lineHeight+2;}g.disableScissor();
 }
}
