package dev.abros.anthub.client;
import com.google.gson.*;import dev.abros.anthub.core.*;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;import java.util.*;
/** Reusable review dialog for real before/after values; bounded and wrapped at every GUI scale. */
final class ChangePreviewScreen extends ScrollScreen {
 private final Screen parent;private final JsonArray changes;private final Runnable apply;private final List<String> lines=new ArrayList<>();private UiDialog layout;private boolean reading;
 static ChangePreviewScreen history(Screen parent,JsonArray changes){var screen=new ChangePreviewScreen(parent,changes,()->{});screen.reading=true;return screen;}
 ChangePreviewScreen(Screen parent,JsonArray changes,Runnable apply){super(Component.literal("Проверить изменения"));this.parent=parent;this.changes=changes.deepCopy();this.apply=apply;}
 @Override protected void init(){layout=UiDialog.fit(width,height,460,330);lines.clear();for(var e:changes){var row=e.getAsJsonObject();for(String text:List.of(Json.str(row,"label"),"Было: "+ChangeSummary.text(row.get("before")),"Станет: "+ChangeSummary.text(row.get("after"))))for(var line:font.getSplitter().splitLines(text,Math.max(10,layout.body().width()-12),net.minecraft.network.chat.Style.EMPTY))lines.add(line.getString());lines.add("");}scrollArea(lines.size(),layout.body(),14);if(reading){UiActions.close(new NativeLayout.Box(layout.footer().x(),layout.footer().bottom()-20,layout.footer().width(),20),this::addRenderableWidget,this::onClose);}else UiActions.row(layout.footer(),this::addRenderableWidget,UiActions.primary("Подтвердить",()->{minecraft.setScreen(parent);apply.run();},true),UiActions.action("Вернуться к правкам",this::onClose,true));}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,layout.frame().x(),layout.frame().y(),layout.frame().width(),layout.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,layout.header().x(),layout.frame().y(),layout.header().width());for(int n=firstRow;n<Math.min(lines.size(),firstRow+visibleRows);n++)Ui.text(g,font,lines.get(n),layout.body().x(),layout.body().y()+(n-firstRow)*14,UiKit.text(),false);});}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
