package dev.abros.anthub.client;
import com.google.gson.JsonObject;import dev.abros.anthub.core.NativeLayout;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;
/** Theme changes are transient until Apply; closing restores the actual saved palette. */
final class ThemePreviewScreen extends ScrollScreen {
 private final Screen parent;private final int original;private int selected;private UiDialog layout;private CommunityCard card;private String error="";
 ThemePreviewScreen(Screen parent){super(Component.literal("Предпросмотр темы"));this.parent=parent;original=selected=UiPalette.selected();}
 @Override protected void init(){layout=UiDialog.fit(width,height,460,340);var b=layout.body();var area=scrollArea(18,b,24);int x=area.content().x(),w=area.content().width();for(int n=firstRow;n<Math.min(18,firstRow+visibleRows);n++){int y=b.y()+(n-firstRow)*24;switch(n){
 case 0->addRenderableWidget(UiActions.button(Component.literal(UiPalette.names().get(selected)+" ▾"),UiActions.Tone.NORMAL,"",button->minecraft.setScreen(new ChoicePopup(this,"Тема",UiPalette.names(),i->{selected=i;UiPalette.preview(i);rebuildWidgets();},button).current(selected))).bounds(x,y,w,20).build());
 case 2->addRenderableWidget(UiFields.text(font,x,y,w,20,"Поиск","Найти предмет",60,"",v->{}));
 case 4->addRenderableWidget(new UiToggle("Показывать уведомления",x,y,w,true,()->{}));
 case 6->{var sample=new JsonObject();sample.addProperty("section","task");sample.addProperty("title","Построить мост");sample.addProperty("attention","7K2P · В работе");sample.addProperty("preview","Материалы: 32 / 64 · Ещё 32");sample.addProperty("footer","Ответственный: вы · Завтра");card=new CommunityCard(x,y,w,70,sample,()->{});if(n+2<firstRow+visibleRows)addRenderableWidget(card);}
 case 10->UiActions.row(new NativeLayout.Box(x,y,w,20),this::addRenderableWidget,UiActions.primary("Сохранить",()->{},true),UiActions.action("Отмена",()->{},true));
 case 12->addRenderableWidget(UiActions.button(Component.literal("Удалить запись"),UiActions.Tone.DANGER,"",button->{}).bounds(x,y,w,20).build());
 default->{}
 }}UiActions.row(layout.footer(),this::addRenderableWidget,UiActions.primary("Применить тему",()->{try{UiPalette.select(selected);minecraft.setScreen(parent);}catch(Exception ex){error="Не удалось сохранить тему";}},true),UiActions.action("Отмена",this::onClose,true));}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,layout.frame().x(),layout.frame().y(),layout.frame().width(),layout.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,layout.header().x(),layout.frame().y(),layout.header().width());for(int n=firstRow;n<Math.min(18,firstRow+visibleRows);n++){String text=switch(n){case 1->"Поле ввода и поиск";case 3->"Переключатель";case 5->"Карточка задачи";case 9->"Основные действия";case 11->"Опасное действие";case 14->"Обычный текст — читается на поверхности";case 15->"Вторичный текст и подсказки";case 16->"Полоса прокрутки использует эту тему";case 17->error;default->"";};if(!text.isEmpty())Ui.text(g,font,UiKit.fit(font,text,layout.body().width()-12),layout.body().x(),layout.body().y()+(n-firstRow)*24,n==15?UiKit.muted():UiKit.text(),false);}});}
 @Override public void onClose(){UiPalette.preview(original);minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
