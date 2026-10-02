package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Selection is limited to actual members; typed text only filters the list. */
final class MemberPickerScreen extends ScrollScreen {
 private final Screen parent;private final JsonArray members;private final Consumer<JsonObject> selected;private String query="";
 MemberPickerScreen(Screen parent,JsonArray members,Consumer<JsonObject> selected){super(Component.literal("Ответственный · участник объединения"));this.parent=parent;this.members=members;this.selected=selected;}
 private int top(){return UiDialog.top(height,300);}private int bottom(){return height-top();}private int w(){return Math.min(380,width-40);}private int x(){return (width-w())/2;}
 private List<JsonObject> matches(){return members.asList().stream().map(JsonElement::getAsJsonObject).filter(j->Json.str(j,"name").toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).sorted(Comparator.comparing(j->Json.str(j,"name"),String.CASE_INSENSITIVE_ORDER)).toList();}
 @Override protected void init(){var search=addRenderableWidget(UiFields.text(font,x(),top()+30,w(),20,Component.literal("Поиск участника")));search.setHint(Component.literal("Поиск по нику"));search.setMaxLength(32);search.setValue(query);search.setResponder(v->{query=v;resetScroll();rebuildWidgets();});var entries=matches();scrollArea(entries.size(),new dev.abros.anthub.core.NativeLayout.Box(x(),top()+60,Math.max(0,w()),Math.max(0,(bottom()-40)-(top()+60))),26);for(int i=firstRow;i<Math.min(entries.size(),firstRow+visibleRows);i++){var player=entries.get(i);addRenderableWidget(UiActions.button(Component.literal(Json.str(player,"name")),UiActions.Tone.NORMAL,"",b->{minecraft.setScreen(parent);selected.accept(player);}).bounds(x(),top()+60+(i-firstRow)*26,w(),22).build());}UiActions.close(new dev.abros.anthub.core.NativeLayout.Box(x(),bottom()-28,w(),20),this::addRenderableWidget,this::onClose);}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());if(matches().isEmpty())Ui.centered(g,font,"Участник не найден",width/2,top()+68,UiPalette.color(0xBAC7D2));});}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
