package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
/** Full-page compound surface for tools that do not belong to the server sidebar. */
final class UiPage {
 static dev.abros.anthub.core.NativeLayout.Box body(int width,int height){return UiWorkspace.fit(width,height).frame().inset(8);}
 static void draw(GuiGraphics g,int width,int height){var frame=UiWorkspace.fit(width,height).frame();UiTheme.panel(g,frame.x(),frame.y(),frame.width(),frame.height(),UiPalette.color(0xBD111A22));}
 private UiPage(){}
}
