package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
final class HomeWidgetsScreen extends Screen {
 private final Screen parent;private String notice="";
 HomeWidgetsScreen(Screen parent){super(Component.literal("Главная: виджеты"));this.parent=parent;}
 private int w(){return Math.min(380,width-32);}private int x(){return (width-w())/2;}private int top(){return UiDialog.top(height,250);}private int end(){return height-top();}
 private void change(String key,int delta){try{if(delta==0)HomeLayout.toggle(key);else HomeLayout.move(key,delta);notice="";}catch(Exception failure){notice="Не удалось сохранить настройки";}rebuildWidgets();}
 @Override protected void init(){var keys=HomeLayout.order();for(int i=0;i<keys.size();i++){String key=keys.get(i);int y=top()+48+i*32;addRenderableWidget(new UiToggle(HomeLayout.name(key),x()+12,y,w()-90,HomeLayout.visible(key),()->change(key,0)));addRenderableWidget(UiActions.button(Component.literal("↑"),UiActions.Tone.NORMAL,"",b->change(key,-1)).bounds(x()+w()-70,y,24,24).build()).active=i>0;addRenderableWidget(UiActions.button(Component.literal("↓"),UiActions.Tone.NORMAL,"",b->change(key,1)).bounds(x()+w()-40,y,24,24).build()).active=i<keys.size()-1;}addRenderableWidget(UiActions.button(Component.literal("По умолчанию"),UiActions.Tone.NORMAL,"",b->{try{HomeLayout.reset();notice="";}catch(Exception failure){notice="Не удалось сохранить";}rebuildWidgets();}).bounds(x()+12,end()-28,120,20).build());addRenderableWidget(UiActions.button(Component.literal("Готово"),UiActions.Tone.NORMAL,"",b->onClose()).bounds(x()+w()-92,end()-28,80,20).build());}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),end());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x()+12,top(),w()-24);if(height>=280||!notice.isEmpty())Ui.status(g,font,notice.isEmpty()?"Просроченные задачи и ближайшие события всегда выше обычных виджетов.":notice,x()+12,Math.max(top()+180,end()-68),w()-24,end()-34);});}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
