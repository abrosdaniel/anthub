package dev.abros.anthub.client;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
/** Shared field initialization; the containing form owns state, validation and submission. */
final class UiFields {
 private static final java.util.Map<net.minecraft.client.gui.components.AbstractWidget,String> issues=new java.util.WeakHashMap<>();
 static void issue(net.minecraft.client.gui.components.AbstractWidget widget,String message){if(message==null||message.isBlank()){issues.remove(widget);widget.setTooltip(null);}else{issues.put(widget,message);widget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(widget.getMessage().getString()+": "+message)));}}
 static int outline(net.minecraft.client.gui.components.AbstractWidget widget){return issues.containsKey(widget)?UiPalette.color(0xFFEF7777):widget.isFocused()?UiKit.accent():UiPalette.outline();}
 static UiEditBox text(Font font,int x,int y,int width,int height,String label,String hint,int limit,String value,Consumer<String> changed){
  var input=new UiEditBox(font,x,y,width,height,Component.literal(label));input.setHint(Component.literal(hint));input.setMaxLength(limit);input.setValue(value);input.setResponder(changed);return input;
 }
 static UiMultiLineEditBox multiline(Font font,int x,int y,int width,int height,String label,String hint,int limit,String value,Consumer<String> changed){
  var input=new UiMultiLineEditBox(font,x,y,width,height,Component.literal(hint),Component.literal(label));input.setCharacterLimit(limit);input.setValue(value);input.setValueListener(changed);return input;
 }
 static UiEditBox text(Font font,int x,int y,int width,int height,Component label){return new UiEditBox(font,x,y,width,height,label);}
 static UiMultiLineEditBox multiline(Font font,int x,int y,int width,int height,Component hint,Component label){return new UiMultiLineEditBox(font,x,y,width,height,hint,label);}
 static PasswordBox password(Font font,int x,int y,int width,Component label,Consumer<net.minecraft.client.gui.components.AbstractWidget> add){var field=new PasswordBox(font,x,y,Math.max(1,width-26),label);add.accept(field);add.accept(new PasswordVisibilityButton(x+width-22,y,field));return field;}
 private UiFields(){}
}
