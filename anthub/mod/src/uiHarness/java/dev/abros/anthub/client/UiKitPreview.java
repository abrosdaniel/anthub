package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Visual catalogue lives only in the test harness, never in the release. */
final class UiKitPreview extends Screen {
 UiKitPreview(){super(Component.literal("UI-Kit · "+UiPalette.name()));}
 private int w(){return Math.min(380,width-32);}private int x(){return (width-w())/2;}private int top(){return UiDialog.top(height,310);}
 @Override protected void init(){int x=x(),y=top()+36,w=w();
  addRenderableWidget(new TabButton(x,y,w/2-2,"Выбранная вкладка",true,()->{}));addRenderableWidget(new TabButton(x+w/2+2,y,w/2-2,"Другая вкладка",false,()->{}));
  addRenderableWidget(new UiChoiceRow(x,y+30,w,"Этап задачи · не выполнен",false,-1,UiKit.ACCENT,()->{}));
  addRenderableWidget(new UiChoiceRow(x,y+54,w,"Этап задачи · выполнен",true,-1,UiKit.ACCENT,()->{}));
  addRenderableWidget(new UiChoiceRow(x,y+78,w,"Вариант голосования · 20",true,0.7,0xFFB49AE8,()->{}));
  var readonly=addRenderableWidget(new UiChoiceRow(x,y+102,w,"Результат · доступен только для чтения",true,0.4,UiKit.ACCENT,()->{}));readonly.active=false;
  addRenderableWidget(new UiToggle("Да",x,y+132,90,true,()->{}));addRenderableWidget(new UiToggle("Нет",x+100,y+132,90,false,()->{}));
  addRenderableWidget(Button.builder(Component.literal("Сохранить"),b->{}).bounds(x,y+164,100,20).build());addRenderableWidget(Button.builder(Component.literal("Удалить…"),b->{}).bounds(x+108,y+164,100,20).build());
  addRenderableWidget(new UiMenuItem("Выбранный пункт меню",x,y+196,w,true,false,()->{}));
  addRenderableWidget(Button.builder(Component.literal("Фильтр ▾"),b->{}).bounds(x,y+226,160,20).build());
 }
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){g.fill(0,0,width,height,UiPalette.color(0xFF101820));UiDialog.draw(g,width,w(),top(),height-top());}
 @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);UiHeading.dialog(g,font,title,x(),top(),w());}
}
