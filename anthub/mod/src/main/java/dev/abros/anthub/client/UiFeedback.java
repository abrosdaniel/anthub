package dev.abros.anthub.client;
import net.minecraft.client.gui.*;
/** Inline result feedback, no modal and no competing toast. */
final class UiFeedback {
 enum Kind { INFO, SUCCESS, ERROR }
 static void draw(GuiGraphics g,Font font,String text,int x,int y,int width,Kind kind){if(text.isBlank())return;int color=color(kind);Ui.text(g,font,UiKit.fit(font,text,width),x,y,color,false);}
 static void draw(GuiGraphics g,Font font,net.minecraft.util.FormattedCharSequence text,int x,int y,int width,Kind kind){int color=color(kind);Ui.text(g,font,text,x,y,color,false);}
 private static int color(Kind kind){return kind==Kind.ERROR?0xFFEF7777:kind==Kind.SUCCESS?0xFF79CBA6:UiKit.muted();}
 private UiFeedback(){}
}
