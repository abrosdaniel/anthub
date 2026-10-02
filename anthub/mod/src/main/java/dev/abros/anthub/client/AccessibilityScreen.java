package dev.abros.anthub.client;
import dev.abros.anthub.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
final class AccessibilityScreen extends ScrollScreen {
 private static boolean loaded,contrast,opaque,compact,serverTime,reducedMotion;private static int density=1,motion=1,decoration=1;private final Screen parent;private String error="";private String tab="Оформление";private CommunityCard preview;
 AccessibilityScreen(Screen parent){super(Component.literal("Интерфейс и оформление"));this.parent=parent;load();}
 private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("anthub/accessibility.json");}
 private static void load(){if(loaded)return;loaded=true;try{if(Files.exists(file())){var j=Json.read(file());contrast=j.has("contrast")&&j.get("contrast").getAsBoolean();opaque=j.has("opaque")&&j.get("opaque").getAsBoolean();compact=j.has("compact")&&j.get("compact").getAsBoolean();reducedMotion=j.has("reducedMotion")&&j.get("reducedMotion").getAsBoolean();serverTime=j.has("serverTime")&&j.get("serverTime").getAsBoolean();density=j.has("density")?Math.max(0,Math.min(2,j.get("density").getAsInt())):compact?0:1;decoration=j.has("decoration")?Math.max(0,Math.min(2,j.get("decoration").getAsInt())):1;motion=j.has("motion")?Math.max(0,Math.min(2,j.get("motion").getAsInt())):reducedMotion?0:1;}}catch(Exception ignored){}}
 static int decoration(){load();return decoration;}
 static boolean animations(){load();return motion!=0;}
 static boolean compact(){load();return density==0;}
 static int density(){load();return density;}
 static int cardStride(){load();return new int[]{60,76,90}[density];}
 static int skinStride(){load();return new int[]{36,46,58}[density];}
 static int motionMillis(){load();return motion==2?240:120;}
 static java.time.ZoneId zone(){load();try{return serverTime?java.time.ZoneId.of(Json.opt(ServerMenuClient.state,"timezone",java.time.ZoneId.systemDefault().getId())):java.time.ZoneId.systemDefault();}catch(Exception ex){return java.time.ZoneId.systemDefault();}}
 static int background(int color){load();return opaque?color|0xFF000000:color;}
 static int foreground(int color){load();return contrast?(UiPalette.light()?0x24131D:0xFFFFFF):UiPalette.color(color);}

 private UiDialog layout;private final java.util.List<Setting> settings=new java.util.ArrayList<>();
 private record Setting(String label,String value,java.util.List<String> choices,int selected,java.util.function.IntConsumer change){}
 private void save(){try{Json.write(file(),java.util.Map.of("contrast",contrast,"opaque",opaque,"compact",compact,"serverTime",serverTime,"reducedMotion",motion==0,"density",density,"motion",motion,"decoration",decoration));error="";}catch(Exception failure){error="Не удалось сохранить настройки";}rebuildWidgets();}
 private void setting(String label,String value,java.util.List<String> choices,int selected,java.util.function.IntConsumer change){settings.add(new Setting(label,value,choices,selected,change));}
 @Override protected void init(){layout=UiDialog.fit(width,height,380,380);settings.clear();preview=null;var frame=layout.frame();var tabs=java.util.List.of("Оформление","Поведение","В игре");UiTabs.build(this,font,new dev.abros.anthub.core.NativeLayout.Box(layout.body().x(),frame.y()+34,layout.body().width(),20),tabs,tabs.indexOf(tab),this::addRenderableWidget,n->{if(n==2){minecraft.setScreen(new HudSettingsScreen(this));return;}tab=tabs.get(n);resetScroll();rebuildWidgets();},true);
  if(tab.equals("Оформление")){
   setting("Тема интерфейса",UiPalette.name(),UiPalette.names(),UiPalette.selected(),n->{});
   setting("Прозрачные панели",opaque?"Выключены":"Включены",java.util.List.of("Включены","Выключены"),opaque?1:0,n->{opaque=n==1;save();});
   setting("Плотность интерфейса",java.util.List.of("Компактная","Сбалансированная","Просторная").get(density),java.util.List.of("Компактная","Сбалансированная","Просторная"),density,n->{density=n;compact=n==0;save();});
   setting("Детали оформления",java.util.List.of("Минимальные","Сдержанные","Выразительные").get(decoration),java.util.List.of("Минимальные","Сдержанные","Выразительные"),decoration,n->{decoration=n;save();});
  }else{
   setting("Масштаб GUI Minecraft",minecraft.options.guiScale().get()==0?"Автоматически":String.valueOf(minecraft.options.guiScale().get()),java.util.List.of("Автоматически","1","2","3","4"),minecraft.options.guiScale().get(),n->{minecraft.options.guiScale().set(n);minecraft.options.save();minecraft.resizeDisplay();});
   setting("Часовой пояс",serverTime?"Серверный":"Местный",java.util.List.of("Местное время","Время сервера"),serverTime?1:0,n->{serverTime=n==1;save();});
   setting("Контрастность",contrast?"Повышенная":"Обычная",java.util.List.of("Обычная","Повышенная"),contrast?1:0,n->{contrast=n==1;save();});
   setting("Анимации",java.util.List.of("Выключены","Лёгкие","Выразительные").get(motion),java.util.List.of("Выключены","Лёгкие","Выразительные"),motion,n->{motion=n;reducedMotion=n==0;save();});
  }
  var body=layout.body();scrollArea(settings.size()*2+4,new dev.abros.anthub.core.NativeLayout.Box(body.x(),body.y()+24,body.width(),Math.max(0,body.height()-24)),22);for(int n=0;n<settings.size();n++){int row=n*2;if(row<firstRow||row+1>=firstRow+visibleRows)continue;var setting=settings.get(n);int y=body.y()+24+(row-firstRow)*22;addRenderableWidget(UiActions.button(Component.literal(setting.value()+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(setting.label().equals("Тема интерфейса")?new ThemePreviewScreen(this):new ChoicePopup(this,setting.label(),setting.choices(),i->setting.change().accept(i),b).current(setting.selected()))).bounds(body.x(),y+16,scrollLayout().content().width(),20).build());}
  int row=settings.size()*2;if(tab.equals("Оформление")&&row>=firstRow&&row+2<firstRow+visibleRows){var sample=new com.google.gson.JsonObject();sample.addProperty("section","task");sample.addProperty("title","Построить мост");sample.addProperty("attention","7K2P · В работе");sample.addProperty("preview","Подготовить материалы");sample.addProperty("footer","Ответственный: вы · Завтра");preview=new CommunityCard(body.x(),body.y()+24+(row-firstRow)*22,scrollLayout().content().width(),62,sample,()->{});addRenderableWidget(preview);}
  UiActions.close(new dev.abros.anthub.core.NativeLayout.Box(layout.footer().right()-UiActions.COMMAND_WIDTH,layout.footer().bottom()-20,UiActions.COMMAND_WIDTH,20),this::addRenderableWidget,this::onClose);
 }
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,layout.frame().x(),layout.frame().y(),layout.frame().width(),layout.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,layout.header().x(),layout.frame().y(),layout.header().width());for(int n=0;n<settings.size();n++){int row=n*2;if(row<firstRow||row+1>=firstRow+visibleRows)continue;Ui.text(g,font,settings.get(n).label(),layout.body().x(),layout.body().y()+24+(row-firstRow)*22,UiKit.text(),false);}if(!error.isEmpty())Ui.status(g,font,error,layout.footer().x(),layout.footer().y(),layout.footer().width(),layout.footer().y()+20);});}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
