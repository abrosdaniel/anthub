package dev.abros.anthub.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
/** Never exposes a password through narration or copy/cut shortcuts. Pasting remains available. */
final class PasswordBox extends EditBox {
 PasswordBox(Font font,int x,int y,int width,Component label){super(font,x,y,width,20,label);setMaxLength(128);setFormatter((text,pos)->FormattedCharSequence.forward("•".repeat(text.length()),Style.EMPTY));}
 @Override protected MutableComponent createNarrationMessage(){return getMessage().copy();}
 @Override public boolean keyPressed(int key,int scan,int modifiers){if(Screen.isCopy(key)||Screen.isCut(key))return true;return super.keyPressed(key,scan,modifiers);}
}
