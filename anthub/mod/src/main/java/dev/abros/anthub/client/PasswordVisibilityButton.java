package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
final class PasswordVisibilityButton extends Button {
 private final PasswordBox field;
 PasswordVisibilityButton(int x,int y,PasswordBox field){super(x,y,22,20,Component.literal("Показать пароль"),b->{field.toggleVisibility();b.setMessage(Component.literal(field.revealed()?"Скрыть пароль":"Показать пароль"));},DEFAULT_NARRATION);this.field=field;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){g.fill(getX(),getY(),getX()+22,getY()+20,isHoveredOrFocused()?0xFF405365:0xFF263440);int c=active?0xFFE0E8F0:0xFF677580;g.fill(getX()+5,getY()+7,getX()+17,getY()+8,c);g.fill(getX()+3,getY()+8,getX()+5,getY()+12,c);g.fill(getX()+17,getY()+8,getX()+19,getY()+12,c);g.fill(getX()+5,getY()+12,getX()+17,getY()+13,c);g.fill(getX()+9,getY()+8,getX()+13,getY()+12,c);if(!field.revealed())for(int i=0;i<14;i++)g.fill(getX()+4+i,getY()+15-i/2,getX()+5+i,getY()+16-i/2,c);if(isFocused())g.renderOutline(getX(),getY(),22,20,c);}
}
