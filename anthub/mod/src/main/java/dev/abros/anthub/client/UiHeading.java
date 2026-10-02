package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
/** Every full page uses the same title baseline and left gutter. */
final class UiHeading {
 private UiHeading(){}
 static void page(GuiGraphics g,Font font,Component title,int width){UiWorkspace.fit(width,240).heading(g,font,title);}
 static void dialog(GuiGraphics g,Font font,Component title,int left,int top,int width){UiTypography.draw(g,font,title.getString(),left+8,top+12,width-16,UiTypography.Role.SECTION);}
}
