package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
/** Small optional location editor; coordinates are published only after the parent form is sent. */
final class LocationEditor extends Screen {
 private final Screen parent;private final Consumer<JsonElement> save;private final boolean mayHide;private boolean privatePlace;private final JsonObject values;private String error="";
 LocationEditor(Screen parent,JsonElement value,boolean mayHide,Consumer<JsonElement> save){super(Component.literal("Место"));this.parent=parent;this.mayHide=mayHide;this.save=save;values=value!=null&&value.isJsonObject()?value.getAsJsonObject().deepCopy():new JsonObject();privatePlace=values.has("membersOnly")&&values.get("membersOnly").getAsBoolean();}
 private int top(){return Math.max(22,(height-220)/2);}private int left(){return (width-Math.min(360,width-24))/2;}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){super.renderBackground(g,x,y,d);DialogPanel.draw(g,width,Math.min(360,width-24),Math.max(4,top()-28),Math.min(height-4,top()+220));}
 @Override protected void init(){int x=left(),w=width-2*x,y=top();String[] keys={"name","dimension","x","y","z"};String[] hints={"Название места","Измерение, например minecraft:overworld","X","Y","Z"};
  for(int i=0;i<keys.length;i++){String key=keys[i];int boxX=i<2?x:x+(i-2)*(w/3),boxY=y+(i<2?i*28:56),boxW=i<2?w:w/3-4;var box=addRenderableWidget(new EditBox(font,boxX,boxY,boxW,20,Component.literal(hints[i])));box.setHint(Component.literal(hints[i]));box.setMaxLength(i==0?80:i==1?160:10);box.setValue(values.has(key)?values.get(key).getAsString():"");box.setResponder(v->values.addProperty(key,v));}
  addRenderableWidget(Button.builder(Component.literal("Моя позиция"),b->{if(minecraft.player==null||minecraft.level==null)return;var pos=minecraft.player.blockPosition();values.addProperty("x",pos.getX());values.addProperty("y",pos.getY());values.addProperty("z",pos.getZ());values.addProperty("dimension",minecraft.level.dimension().location().toString());if(Json.opt(values,"name","").isBlank())values.addProperty("name","Место встречи");rebuildWidgets();}).bounds(x,y+84,w,20).build());
  if(mayHide)addRenderableWidget(Button.builder(Component.literal(privatePlace?"Только участникам объединения":"Видно всем"),b->{privatePlace=!privatePlace;b.setMessage(Component.literal(privatePlace?"Только участникам объединения":"Видно всем"));}).bounds(x,y+108,w,20).build());
  addRenderableWidget(Button.builder(Component.literal("Готово"),b->{try{var j=values.deepCopy();for(String k:new String[]{"x","y","z"})j.addProperty(k,Integer.parseInt(values.has(k)?values.get(k).getAsString().strip():""));j.addProperty("membersOnly",mayHide&&privatePlace);save.accept(CommunityLocation.read(j).json());minecraft.setScreen(parent);}catch(Exception ex){error=ex.getMessage();}}).bounds(x,y+144,w/2-3,20).build());
  addRenderableWidget(Button.builder(Component.literal("Убрать место"),b->{save.accept(JsonNull.INSTANCE);minecraft.setScreen(parent);}).bounds(x+w/2+3,y+144,w/2-3,20).build());addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x,y+170,w,20).build());
 }
 @Override public void render(GuiGraphics g,int x,int y,float delta){super.render(g,x,y,delta);g.drawCenteredString(font,title,width/2,top()-18,0xE2BE75);Ui.status(g,font,error,left(),top()+195,width-2*left(),height-2);}
 @Override public void tick(){if(!ServerMenuClient.available())minecraft.setScreen(null);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
