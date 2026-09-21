package dev.abros.anthub.client;
import dev.abros.anthub.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
final class AccessibilityScreen extends Screen {
 private static boolean loaded,contrast,opaque;private final Screen parent;private String error="";
 AccessibilityScreen(Screen parent){super(Component.literal("Доступность интерфейса"));this.parent=parent;load();}
 private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("anthub/accessibility.json");}
 private static void load(){if(loaded)return;loaded=true;try{if(Files.exists(file())){var j=Json.read(file());contrast=j.has("contrast")&&j.get("contrast").getAsBoolean();opaque=j.has("opaque")&&j.get("opaque").getAsBoolean();}}catch(Exception ignored){}}
 static int background(int color){load();return opaque?color|0xFF000000:color;}
 static int foreground(int color){load();return contrast?0xFFFFFF:color;}
 private void save(){try{Json.write(file(),java.util.Map.of("contrast",contrast,"opaque",opaque));error="";}catch(Exception failure){error="Не удалось сохранить настройки";}rebuildWidgets();}
 @Override protected void init(){int w=Math.min(300,width-24),left=(width-w)/2,top=Math.max(36,height/2-60);
  addRenderableWidget(Button.builder(Component.literal("Контрастность: "+(contrast?"повышенная":"обычная")),b->{contrast=!contrast;save();}).bounds(left,top,w,20).build());
  addRenderableWidget(Button.builder(Component.literal("Непрозрачные панели: "+(opaque?"да":"нет")),b->{opaque=!opaque;save();}).bounds(left,top+26,w,20).build());
  addRenderableWidget(Button.builder(Component.literal("Масштаб GUI Minecraft: "+minecraft.options.guiScale().get()),b->{int value=minecraft.options.guiScale().get();minecraft.options.guiScale().set(value>=4?1:value+1);minecraft.options.save();minecraft.resizeDisplay();}).bounds(left,top+52,w,20).build());
  addRenderableWidget(Button.builder(Client.tr("back"),b->onClose()).bounds(left,height-28,w,20).build());
 }
 @Override public void render(GuiGraphics g,int x,int y,float delta){super.render(g,x,y,delta);g.drawCenteredString(font,title,width/2,16,0xFFFFFF);Ui.status(g,font,error.isEmpty()?"Масштаб меняет размер текста и кнопок во всём Minecraft. Настройки сохраняются только на этом клиенте.":error,Math.max(12,(width-300)/2),Math.max(122,height/2+24),Math.min(300,width-24),height-34);}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
