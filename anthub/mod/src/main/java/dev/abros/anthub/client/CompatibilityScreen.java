package dev.abros.anthub.client;
import dev.abros.anthub.AntHub;
import dev.abros.anthub.core.Manifest;
import dev.abros.anthub.core.Versions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Explains a pack requirement before offering an update in its compatibility branch. */
final class CompatibilityScreen extends Screen {
 private final Screen parent;private final Manifest manifest;
 CompatibilityScreen(Screen parent,Manifest manifest){super(Component.literal("Совместимость сборки"));this.parent=parent;this.manifest=manifest;}
 private int w(){return Math.min(360,width-24);}private int x(){return (width-w())/2;}private int top(){return DialogPanel.top(height,220);}
 @Override protected void init(){boolean core=!Versions.supportsRequirement(AntHub.VERSION,manifest.anthubVersion());var update=addRenderableWidget(Button.builder(Component.literal("Подобрать версию AntHub"),b->minecraft.setScreen(new CoreVersionsPopup(this,manifest.anthubVersion()))).bounds(x()+10,top()+150,w()-20,20).build());update.active=core&&Client.pending.isEmpty();addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x()+10,top()+178,w()-20,20).build());}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){super.renderBackground(g,x,y,d);DialogPanel.draw(g,width,w(),top(),top()+208);}
 @Override public void render(GuiGraphics g,int mx,int my,float d){super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());Ui.text(g,font,"Установлен AntHub: "+AntHub.VERSION,x()+10,top()+38,UiKit.text());Ui.text(g,font,"Требуется AntHub: "+manifest.anthubVersion(),x()+10,top()+58,UiKit.text());Ui.wrap(g,font,Component.literal("Сборка: Minecraft "+manifest.minecraft()+", NeoForge "+manifest.neoForge()+".\n\n"+Client.hub.incompatibility(manifest)+" — установленная версия не соответствует требованию сборки. Для другой версии Minecraft или NeoForge нужен соответствующий профиль запуска."),x()+10,top()+82,w()-20,UiKit.muted());}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
