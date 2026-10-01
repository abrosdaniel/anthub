package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Compact menu row; text aligns with adjacent choices rather than moving with its length. */
final class UiMenuItem extends Button {
 private final boolean selected;private final String label;
 UiMenuItem(String label,int x,int y,int width,boolean selected,Runnable choose){super(x,y,width,20,Component.literal((selected?"✓ ":"")+label),b->choose.run(),DEFAULT_NARRATION);this.label=label;this.selected=selected;}
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float delta){int left=getX(),top=getY(),w=getWidth();boolean hover=isHoveredOrFocused(),danger=label.startsWith("Удалить");g.fill(left,top,left+w,top+20,hover?(danger?UiPalette.color(0xFF503039):UiPalette.color(0xFF304957)):UiPalette.color(0xFF172630));int color=danger?UiPalette.color(0xFFEF9090):selected?UiPalette.color(0xFF83C6C4):UiPalette.color(0xFFE0E9EE);if(selected)UiIcons.draw(g,UiIcons.CHECK,left+4,top+4,color);var font=Minecraft.getInstance().font;String shown=font.width(label)>w-28?font.plainSubstrByWidth(label,w-28-font.width("…"))+"…":label;Ui.text(g,font,shown,left+22,top+6,color,false);if(isFocused())g.renderOutline(left,top,w,20,UiPalette.color(0xFF83C6C4));}
}
