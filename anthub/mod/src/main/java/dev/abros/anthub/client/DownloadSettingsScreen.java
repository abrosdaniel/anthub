package dev.abros.anthub.client;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
final class DownloadSettingsScreen extends Screen {
 private final Screen parent;private DownloadSettings settings;private String notice="";private boolean busy;
 DownloadSettingsScreen(Screen parent){super(Component.literal("Загрузки сборки"));this.parent=parent;settings=Client.hub.downloadSettings();}
 private int w(){return Math.min(360,width-32);}private int x(){return(width-w())/2;}private int top(){return DialogPanel.top(height,230);}private int bottom(){return height-top();}
 private void save(DownloadSettings next){settings=next;busy=true;Client.IO.execute(()->{String error="";try{Client.hub.downloadSettings(next);}catch(Exception e){error=Errors.message(e);}String text=error;minecraft.execute(()->{busy=false;notice=text;rebuildWidgets();});});}
 @Override protected void init(){var parallel=addRenderableWidget(Button.builder(Component.literal("Одновременных загрузок: "+settings.parallel()+" ▾"),b->minecraft.setScreen(new ChoicePopup(this,"Загрузки",List.of("1","2","3","4"),n->save(new DownloadSettings(n+1,settings.limitMiB(),settings.mirror())),b))).bounds(x(),top()+36,w(),20).build());parallel.active=!busy;var speed=addRenderableWidget(Button.builder(Component.literal("Скорость: "+(settings.limitMiB()==0?"без ограничения":settings.limitMiB()+" МиБ/с")+" ▾"),b->minecraft.setScreen(new ChoicePopup(this,"Скорость",List.of("Без ограничения","1 МиБ/с","5 МиБ/с","10 МиБ/с","20 МиБ/с"),n->save(new DownloadSettings(settings.parallel(),List.of(0,1,5,10,20).get(n),settings.mirror())),b))).bounds(x(),top()+64,w(),20).build());speed.active=!busy;var mirror=addRenderableWidget(Button.builder(Component.literal("Источник: "+switch(settings.mirror()){case "modrinth"->"Modrinth";case "curseforge"->"CurseForge";default->"по порядку сборки";}+" ▾"),b->minecraft.setScreen(new ChoicePopup(this,"Источник",List.of("По порядку сборки","Modrinth","CurseForge"),n->save(new DownloadSettings(settings.parallel(),settings.limitMiB(),List.of("auto","modrinth","curseforge").get(n))),b))).bounds(x(),top()+92,w(),20).build());mirror.active=!busy;addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x(),bottom()-28,w(),20).build());}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){super.renderBackground(g,mx,my,d);DialogPanel.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());Ui.status(g,font,notice.isEmpty()?"Если выбранный источник недоступен, загрузка продолжится с другого.":notice,x(),top()+124,w(),bottom()-34);}
 @Override public void onClose(){if(!busy)minecraft.setScreen(parent);}
}
