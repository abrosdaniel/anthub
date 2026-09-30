package dev.abros.anthub.client;
import dev.abros.anthub.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
final class AccessibilityScreen extends Screen {
 private static boolean defaultChecked;
 static void applyGuiDefault(){if(defaultChecked)return;defaultChecked=true;var mc=Minecraft.getInstance();var marker=mc.gameDirectory.toPath().resolve("anthub/gui-default.json");if(Files.exists(marker))return;try{Json.write(marker,java.util.Map.of("version",1));mc.options.guiScale().set(3);mc.options.save();mc.resizeDisplay();}catch(Exception failure){Client.failure(failure);}}
 private static boolean loaded,contrast,opaque,compact,serverTime,reducedMotion;private final Screen parent;private String error="";private String tab="Оформление";private CommunityCard preview;
 AccessibilityScreen(Screen parent){super(Component.literal("Интерфейс и оформление"));this.parent=parent;load();}
 private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("anthub/accessibility.json");}
 private static void load(){if(loaded)return;loaded=true;try{if(Files.exists(file())){var j=Json.read(file());contrast=j.has("contrast")&&j.get("contrast").getAsBoolean();opaque=j.has("opaque")&&j.get("opaque").getAsBoolean();compact=j.has("compact")&&j.get("compact").getAsBoolean();reducedMotion=j.has("reducedMotion")&&j.get("reducedMotion").getAsBoolean();serverTime=j.has("serverTime")&&j.get("serverTime").getAsBoolean();}}catch(Exception ignored){}}
 static boolean animations(){load();return !reducedMotion;}
 static boolean compact(){load();return compact;}
 static java.time.ZoneId zone(){load();try{return serverTime?java.time.ZoneId.of(Json.opt(ServerMenuClient.state,"timezone",java.time.ZoneId.systemDefault().getId())):java.time.ZoneId.systemDefault();}catch(Exception ex){return java.time.ZoneId.systemDefault();}}
 static int background(int color){load();return opaque?color|0xFF000000:color;}
 static int foreground(int color){load();return contrast?0xFFFFFF:color;}
 private int panelTop(){return DialogPanel.top(height,340);}
 private int panelBottom(){return height-panelTop();}
 private void save(){try{Json.write(file(),java.util.Map.of("contrast",contrast,"opaque",opaque,"compact",compact,"serverTime",serverTime,"reducedMotion",reducedMotion));error="";}catch(Exception failure){error="Не удалось сохранить настройки";}rebuildWidgets();ButtonHints.apply(this);}
 private int panelWidth(){return Math.min(380,width-24);}
 private int left(){return (width-panelWidth())/2;}
 private void choice(String label,String value,int y,java.util.List<String> choices,int selected,java.util.function.IntConsumer select){
  var button=addRenderableWidget(Button.builder(Component.literal(value+" ▾"),b->minecraft.setScreen(new ChoicePopup(this,label,choices,n->select.accept(n),b).current(selected))).bounds(left()+panelWidth()/2,y,panelWidth()/2-12,20).build());

 }
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){super.renderBackground(g,x,y,d);DialogPanel.draw(g,width,panelWidth(),panelTop(),panelBottom());}
 @Override protected void init(){int w=panelWidth(),left=left(),top=panelTop();preview=null;
  for(int i=0;i<2;i++){String name=java.util.List.of("Оформление","Поведение").get(i);addRenderableWidget(new TabButton(left+12+i*((w-28)/2+4),top+34,(w-28)/2,name,tab.equals(name),()->{tab=name;rebuildWidgets();}));}
  if(tab.equals("Оформление")){
   choice("Тема интерфейса",UiPalette.name(),top+66,UiPalette.names(),UiPalette.selected(),n->{try{UiPalette.select(n);error="";}catch(Exception failure){error="Не удалось сохранить тему";}rebuildWidgets();});
   addRenderableWidget(new UiToggle("Прозрачные панели",left+12,top+94,w-24,!opaque,()->{opaque=!opaque;save();}));
   choice("Карточки списков",compact?"Короткие":"Подробные",top+128,java.util.List.of("Подробные — с дополнительной строкой","Короткие — больше записей на экране"),compact?1:0,n->{compact=n==1;save();});
   var sample=new com.google.gson.JsonObject();sample.addProperty("section","task");sample.addProperty("title","Построить мост");sample.addProperty("attention","7K2P · В работе");sample.addProperty("preview","Подготовить материалы");sample.addProperty("footer","Ответственный: вы · Завтра");preview=new CommunityCard(left+12,top+174,w-24,compact?54:70,sample,()->{});
  }else{
   choice("Масштаб GUI Minecraft",minecraft.options.guiScale().get()==0?"Автоматически":String.valueOf(minecraft.options.guiScale().get()),top+66,java.util.List.of("Автоматически","1","2","3","4"),minecraft.options.guiScale().get(),n->{minecraft.options.guiScale().set(n);minecraft.options.save();minecraft.resizeDisplay();});
   choice("Часовой пояс",serverTime?"Серверный":"Местный",top+98,java.util.List.of("Местное время","Время сервера"),serverTime?1:0,n->{serverTime=n==1;save();});
   choice("Контрастность",contrast?"Повышенная":"Обычная",top+130,java.util.List.of("Обычная","Повышенная"),contrast?1:0,n->{contrast=n==1;save();});
   addRenderableWidget(new UiToggle("Плавная подсветка",left+12,top+162,w-24,!reducedMotion,()->{reducedMotion=!reducedMotion;save();}));
  }
  addRenderableWidget(Button.builder(Component.literal("Готово"),b->onClose()).bounds(left+w-92,panelBottom()-28,80,20).build());
 }
 @Override public void render(GuiGraphics g,int x,int y,float delta){super.render(g,x,y,delta);int left=left(),top=panelTop(),w=panelWidth();UiHeading.dialog(g,font,title,left,top,w);
  if(tab.equals("Оформление")){g.drawString(font,"Тема интерфейса",left+12,top+72,UiKit.text(),false);g.drawString(font,"Карточки списков",left+12,top+134,UiKit.text(),false);g.drawString(font,"Предпросмотр",left+12,top+158,UiKit.muted(),false);if(preview!=null)preview.render(g,-10000,-10000,delta);}
  else{for(int i=0;i<3;i++)g.drawString(font,java.util.List.of("Масштаб GUI Minecraft","Часовой пояс","Контрастность").get(i),left+12,top+72+i*32,UiKit.text(),false);}
  Ui.status(g,font,error.isEmpty()?tab.equals("Оформление")?"Тема применяется сразу. Короткие карточки показывают больше записей на экране.":"Масштаб меняет весь Minecraft. Даты задач и событий используют выбранный часовой пояс. Настройки сохраняются на этом клиенте.":error,left+12,top+(tab.equals("Оформление")?254:206),w-24,panelBottom()-40);
 }
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
