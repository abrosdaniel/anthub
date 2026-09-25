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
 private int top(){return DialogPanel.top(height,300);}private int bottom(){return height-top();}private int w(){return Math.min(380,width-40);}private int x(){return (width-w())/2;}
 private List<JsonObject> matches(){return members.asList().stream().map(JsonElement::getAsJsonObject).filter(j->Json.str(j,"name").toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).sorted(Comparator.comparing(j->Json.str(j,"name"),String.CASE_INSENSITIVE_ORDER)).toList();}
 @Override protected void init(){var search=addRenderableWidget(new EditBox(font,x(),top()+30,w(),20,Component.literal("Поиск участника")));search.setHint(Component.literal("Поиск по нику"));search.setMaxLength(32);search.setValue(query);search.setResponder(v->{query=v;resetScroll();rebuildWidgets();});var entries=matches();scrollArea(entries.size(),top()+60,bottom()-40,26,x()+w()+4);for(int i=firstRow;i<Math.min(entries.size(),firstRow+visibleRows);i++){var player=entries.get(i);addRenderableWidget(Button.builder(Component.literal(Json.str(player,"name")),b->{minecraft.setScreen(parent);selected.accept(player);}).bounds(x(),top()+60+(i-firstRow)*26,w(),22).build());}addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x(),bottom()-28,w(),20).build());}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){super.renderBackground(g,mx,my,d);DialogPanel.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){super.render(g,mx,my,d);g.drawCenteredString(font,title,width/2,top()+10,0xE2BE75);if(matches().isEmpty())g.drawCenteredString(font,"Участник не найден",width/2,top()+68,0xBAC7D2);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
