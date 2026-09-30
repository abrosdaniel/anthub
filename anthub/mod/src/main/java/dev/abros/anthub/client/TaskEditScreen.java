package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Local editor: validation and discard warning precede the parent's receipt-protected mutation. */
final class TaskEditScreen extends Screen implements CommunityScreen.Receiver {
 private final Screen parent;private final List<CommunityScreen.Field> fields;private final JsonObject original,values;private final Consumer<JsonObject> submit;private String error="";private boolean busy,uncertain;private long sentAt;private final Map<String,Integer> ys=new HashMap<>();
 TaskEditScreen(Screen parent,String title,List<CommunityScreen.Field> fields,JsonObject preset,Consumer<JsonObject> submit){super(Component.literal(title));this.parent=parent;this.fields=fields;this.values=preset.deepCopy();for(var f:fields)if(!values.has(f.key()))values.addProperty(f.key(),"");this.original=values.deepCopy();this.submit=submit;}
 private int w(){return Math.min(440,width-40);}private int x(){return (width-w())/2;}private int top(){return DialogPanel.top(height,fields.size()==1?200:290);}private int bottom(){return height-top();}
 @Override protected void init(){ys.clear();int y=top()+38;for(var f:fields){ys.put(f.key(),y);boolean multi=Set.of("description","text").contains(f.key());int h=multi?Math.max(36,Math.min(80,bottom()-y-88)):20;if(multi){var box=addRenderableWidget(new MultiLineEditBox(font,x(),y+14,w(),h,Component.literal(f.label()),Component.literal(f.label())));box.setCharacterLimit(f.limit());box.setValue(Json.str(values,f.key()));box.setValueListener(v->values.addProperty(f.key(),v));}else{var box=addRenderableWidget(new EditBox(font,x(),y+14,w(),20,Component.literal(f.label())));box.setMaxLength(f.limit());box.setValue(Json.str(values,f.key()));box.setResponder(v->values.addProperty(f.key(),v));}y+=h+30;}for(var child:children())if(child instanceof AbstractWidget input)input.active=!busy;
  addRenderableWidget(Button.builder(Component.literal(busy?"Сохраняем…":"Сохранить"),b->{if(busy||uncertain)return;if(fields.stream().anyMatch(f->!Set.of("description","text").contains(f.key())&&Json.str(values,f.key()).isBlank())){error="Заполните название";return;}if(parent instanceof TaskScreen){busy=true;sentAt=System.currentTimeMillis();rebuildWidgets();submit.accept(values.deepCopy());}else{minecraft.setScreen(parent);submit.accept(values.deepCopy());}}).bounds(x(),bottom()-28,(w()-8)/2,20).build()).active=!busy&&!uncertain;addRenderableWidget(Button.builder(Component.literal("Отмена"),b->onClose()).bounds(x()+(w()+8)/2,bottom()-28,(w()-8)/2,20).build());}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){super.renderBackground(g,mx,my,d);DialogPanel.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());for(var f:fields)g.drawString(font,f.label(),x(),ys.get(f.key()),UiPalette.color(0xD7E2EC));Ui.status(g,font,error,x(),bottom()-62,w(),bottom()-30);}
 @Override public void onClose(){if(busy){error="Дождитесь ответа сервера";return;}if(values.equals(original)){minecraft.setScreen(parent);return;}minecraft.setScreen(new ConfirmScreen(yes->minecraft.setScreen(yes?parent:this),Component.literal("Отменить изменения?"),Component.literal("Введённый текст не будет сохранён.")));}
 public void receiveCommunity(JsonObject j){if(!(parent instanceof TaskScreen task)||!busy||!task.matches(j))return;task.receiveCommunity(j);busy=false;if(j.has("error")){error=Json.opt(j,"text","Не удалось сохранить изменения");rebuildWidgets();}else minecraft.setScreen(parent);}
 @Override public void tick(){if(busy&&System.currentTimeMillis()-sentAt>=15000){if(parent instanceof TaskScreen task)task.tick();busy=false;uncertain=true;error="Ответ не получен. Вернитесь к задаче и нажмите «Повторить». Введённый текст сохранён.";rebuildWidgets();}}
 @Override public boolean isPauseScreen(){return false;}
}
