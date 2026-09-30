package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** A selected tab is a location indicator, distinct from an unavailable action. */
class TabButton extends Button {
 private final boolean selected;
 TabButton(int x,int y,int w,String label,boolean selected,Runnable action){super(x,y,w,20,Component.literal(label),b->action.run(),DEFAULT_NARRATION);this.selected=selected;active=!selected;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){int x=getX(),y=getY(),w=getWidth();float t=UiTheme.hover(this);g.fill(x,y,x+w,y+20,selected?UiPalette.color(0xFF253E45):UiTheme.mix(UiPalette.color(0x5017232D),UiPalette.color(0xC02D414F),t));g.fill(x,y+19,x+w,y+20,selected?UiKit.accent():UiPalette.color(0xFF3E515F));var font=Minecraft.getInstance().font;String label=font.plainSubstrByWidth(getMessage().getString(),w-12);g.drawString(font,label,x+(w-font.width(label))/2,y+6,AccessibilityScreen.foreground(selected?UiPalette.color(0xD5F4EB):UiPalette.color(0xAFBFCD)),false);if(isFocused())g.renderOutline(x,y,w,20,UiKit.accent());}
}
