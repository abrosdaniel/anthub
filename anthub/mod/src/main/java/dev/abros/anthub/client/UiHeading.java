package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
/** Every full page uses the same title baseline and left gutter. */
final class UiHeading {
 private UiHeading(){}
 static void page(GuiGraphics g,Font font,Component title,int width){Ui.text(g,font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(title,Math.max(1,width-(width<420?100:166)))),16,18,UiPalette.color(0xE2BE75),false);}
 static void dialog(GuiGraphics g,Font font,Component title,int left,int top,int width){Ui.text(g,font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(title,Math.max(1,width-16))),left+8,top+12,UiPalette.color(0xE2BE75),false);}
}
