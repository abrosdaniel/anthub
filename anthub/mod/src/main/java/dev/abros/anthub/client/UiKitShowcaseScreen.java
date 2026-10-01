package dev.abros.anthub.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Internal component catalogue; enabled only by the developer UI harness. */
final class UiKitShowcaseScreen extends ScrollScreen {
 private final Screen parent;private boolean checked=true;
 UiKitShowcaseScreen(Screen parent){super(Component.literal("UI Kit · компоненты"));this.parent=parent;}
 private int x(){return (width-Math.min(400,width-32))/2;}private int w(){return Math.min(400,width-32);}
 @Override protected void init(){scrollArea(8,52,height-42,38,x()+w()-6);for(int n=firstRow;n<Math.min(8,firstRow+visibleRows);n++){int y=52+(n-firstRow)*38;switch(n){case 0->addRenderableWidget(Button.builder(Component.literal("Тема: "+UiPalette.name()+" ▾"),b->minecraft.setScreen(new ChoicePopup(this,"Тема",UiPalette.names(),i->{UiPalette.preview(i);rebuildWidgets();},b))).bounds(x(),y,w()-14,24).build());case 1->addRenderableWidget(new UiChoiceRow(x(),y,w()-14,"Единая отметка · этап / голос / выбор",checked,0.7,UiKit.ACCENT,()->{checked=!checked;rebuildWidgets();}));case 2->addRenderableWidget(new UiToggle("Переключатель",x(),y,w()-14,checked,()->{checked=!checked;rebuildWidgets();}));case 3->{var b=addRenderableWidget(Button.builder(Component.literal("Недоступное действие"),v->{}).bounds(x(),y,w()-14,24).build());b.active=false;}case 4->addRenderableWidget(new UiEditBox(font,x(),y,w()-14,24,Component.literal("Поиск")));case 5->{var b=new UiMultiLineEditBox(font,x(),y,w()-22,30,Component.literal("Описание"),Component.literal("Описание"));b.setValue("Редактируемый текст и перенос строки");addRenderableWidget(b);}case 6->addRenderableWidget(Button.builder(Component.literal("+ Создать"),b->{}).bounds(x(),y,130,24).build());case 7->addRenderableWidget(Button.builder(Component.literal("Удалить…"),b->{}).bounds(x(),y,130,24).build());}}addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x()+w()-100,height-28,86,20).build());}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){super.renderBackground(g,x,y,d);DialogPanel.draw(g,width,w()+24,8,height-8);}
 @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);UiHeading.dialog(g,font,title,x(),12,w());}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
