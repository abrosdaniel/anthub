package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
/** Every full page uses the same title baseline and left gutter. */
final class UiHeading {
 private UiHeading(){}
 static void page(GuiGraphics g,Font font,Component title,int width){g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(title,Math.max(40,width-180))),16,18,UiPalette.color(0xE2BE75),false);}
 static void dialog(GuiGraphics g,Font font,Component title,int left,int top,int width){g.drawString(font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(title,Math.max(1,width))),left,top+12,UiPalette.color(0xE2BE75),false);}
}
