package dev.abros.anthub.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
/** Context and a next step, without an inactive giant button. */
final class UiEmptyState {
 private UiEmptyState(){}
 static void draw(GuiGraphics g,Font font,String title,String hint,int x,int y,int width,int bottom){UiState.draw(g,font,UiState.Kind.EMPTY,title,hint,x,y,width,bottom);}
}
