package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
/** All AntHub confirmations share the dialog and footer components; vanilla is untouched. */
final class UiConfirmDialog extends Screen {
 private final Screen parent;private final Consumer<Boolean> answer;private final Component message;private final ContentPane content=new ContentPane();private UiDialog dialog;private boolean answered,dangerous;
 UiConfirmDialog dangerous(){dangerous=true;return this;}
 UiConfirmDialog(Consumer<Boolean> answer,Component title,Component message){super(title);this.parent=Minecraft.getInstance().screen;this.answer=answer;this.message=message;}
 @Override protected void init(){dialog=UiDialog.fit(width,height,360,220);var body=dialog.body();content.bounds(body.x(),body.y(),body.width(),body.height());content.text(message.getString());var footer=dialog.footer();UiActions.row(new dev.abros.anthub.core.NativeLayout.Box(footer.x(),footer.bottom()-20,footer.width(),20),this::addRenderableWidget,dangerous?UiActions.danger("Подтвердить удаление",()->finish(true),true):UiActions.primary("Подтвердить",()->finish(true),true),UiActions.action("Отмена",()->finish(false),true));}
 private void finish(boolean yes){if(answered)return;answered=true;answer.accept(yes);}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){var f=dialog.frame();UiDialog.surface(g,f.x(),f.y(),f.width(),f.height());}
 @Override public void render(GuiGraphics g,int x,int y,float delta){UiDialog.render(parent,this,g,delta,()->{super.render(g,x,y,delta);var h=dialog.header();UiHeading.dialog(g,font,title,h.x(),dialog.frame().y(),h.width());content.render(g,font);});}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){return content.scroll(x,y,dy)||super.mouseScrolled(x,y,dx,dy);}
 @Override public boolean mouseClicked(double x,double y,int button){return button==0&&content.click(x,y)||super.mouseClicked(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return button==0&&content.drag(y)||super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){content.release();return super.mouseReleased(x,y,button);}
 @Override public void onClose(){finish(false);}
 @Override public boolean isPauseScreen(){return false;}
}
