package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Whole control is clickable; the track and caption both indicate the saved state. */
final class UiToggle extends Button {
 private final boolean enabled;
 UiToggle(String value,int x,int y,int width,boolean enabled,Runnable change){super(x,y,width,24,Component.literal(value),b->change.run(),DEFAULT_NARRATION);this.enabled=enabled;}
 boolean checked(){return enabled;}
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float delta){int left=getX(),top=getY(),w=getWidth();g.fill(left,top,left+w,top+24,isHoveredOrFocused()?UiPalette.color(0xFF2B414B):UiPalette.color(0xFF202F39));int track=left+w-34;g.fill(track,top+6,track+26,top+18,enabled?UiPalette.color(0xFF366B59):UiPalette.color(0xFF48535B));int knob=track+(enabled?15:1);g.fill(knob,top+7,knob+10,top+17,active?(enabled?UiPalette.color(0xFF99D8B9):UiPalette.color(0xFFABB5BE)):UiPalette.color(0xFF78828B));g.drawString(Minecraft.getInstance().font,getMessage(),left+8,top+8,active?UiPalette.color(0xFFE0E9EE):UiPalette.color(0xFF82909C),false);if(isFocused())g.renderOutline(left,top,w,24,UiPalette.color(0xFF83C6C4));}
}
