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
final class TaskEditScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private final List<CommunityScreen.Field> fields;private final JsonObject original,values;private final Consumer<JsonObject> submit;private String error="",invalidField="";private boolean busy,uncertain,validationDirty;private long sentAt;private final Map<String,Integer> ys=new HashMap<>();
 TaskEditScreen(Screen parent,String title,List<CommunityScreen.Field> fields,JsonObject preset,Consumer<JsonObject> submit){super(Component.literal(title));this.parent=parent;this.fields=fields;this.values=preset.deepCopy();for(var f:fields){var value=values.get(f.key());values.addProperty(f.key(),value!=null&&value.isJsonPrimitive()?value.getAsString():"");}this.original=values.deepCopy();this.submit=submit;}
 private java.util.List<UiFormGrid.Cell> grid(){return UiFormGrid.layout(fields.stream().map(f->new UiFormGrid.Field(f,true,Set.of("description","text").contains(f.key()),"",invalidField.equals(f.key()))).toList(),Math.max(0,Math.min(440,width-40)-10));}
 private UiDialog dialog(){return UiDialog.fit(width,height,440,38+UiFormGrid.units(grid())*22+70);}
 private int w(){return dialog().body().width();}private int x(){return dialog().body().x();}private int top(){return dialog().frame().y();}private int bottom(){return dialog().frame().bottom();}
 private void editValue(String key,String value){values.addProperty(key,value);if(invalidField.equals(key)){invalidField="";error="";validationDirty=true;}}
 private void save(){if(busy||uncertain)return;invalidField="";error="";var missing=fields.stream().filter(f->!Set.of("description","text").contains(f.key())&&Json.str(values,f.key()).isBlank()).findFirst();if(missing.isPresent()){invalidField=missing.get().key();error="Заполните поле «"+missing.get().label()+"»";for(var cell:grid())if(cell.field().key().equals(invalidField))restoreScroll(cell.row());rebuildWidgets();for(var child:children())if(child instanceof AbstractWidget widget&&widget.getMessage().getString().equals(missing.get().label())){setFocused(child);break;}return;}if(parent instanceof TaskScreen){busy=true;sentAt=System.currentTimeMillis();rebuildWidgets();submit.accept(values.deepCopy());}else{minecraft.setScreen(parent);submit.accept(values.deepCopy());}}
 @Override protected void init(){ys.clear();scrollArea(UiFormGrid.units(grid()),new dev.abros.anthub.core.NativeLayout.Box(x(),top()+38,Math.max(0,w()),Math.max(0,(bottom()-70)-(top()+38))),22);
  for(var cell:grid()){if(!cell.visible(firstRow,visibleRows))continue;var f=cell.field();var area=cell.bounds(x(),top()+38,scrollLayout().content().width(),firstRow);ys.put(f.key(),area.y());
   if(Set.of("description","text").contains(f.key()))addRenderableWidget(UiFields.multiline(font,area.x(),area.y()+13,area.width()-8,38,f.label(),f.label(),f.limit(),Json.str(values,f.key()),v->editValue(f.key(),v)));
   else addRenderableWidget(UiFields.text(font,area.x(),area.y()+13,area.width(),20,f.label(),f.label(),f.limit(),Json.str(values,f.key()),v->editValue(f.key(),v)));
  }
  for(var child:children())if(child instanceof AbstractWidget input){input.active=!busy;if(fields.stream().anyMatch(f->f.key().equals(invalidField)&&f.label().equals(input.getMessage().getString())))UiFields.issue(input,error);}
  UiActions.row(new dev.abros.anthub.core.NativeLayout.Box(x(),bottom()-28,w(),20),this::addRenderableWidget,
   UiActions.primary(busy?"Сохраняем…":"Сохранить",this::save,!busy&&!uncertain).withIcon(UiIcons.SAVE),UiActions.action("Отмена",this::onClose,true));}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());for(var f:fields)if(ys.containsKey(f.key()))Ui.text(g,font,f.label(),x(),ys.get(f.key()),UiPalette.color(0xD7E2EC));for(var cell:grid())if(cell.field().key().equals(invalidField)&&cell.visible(firstRow,visibleRows))Ui.text(g,font,UiKit.fit(font,error,w()),x(),top()+38+(cell.row()-firstRow)*22+(Set.of("description","text").contains(cell.field().key())?68:36),UiPalette.color(0xFFEF7777),false);if(invalidField.isEmpty())Ui.status(g,font,error,x(),bottom()-62,w(),bottom()-30);});}
 @Override public void onClose(){if(busy){error="Дождитесь ответа сервера";return;}if(values.equals(original)){minecraft.setScreen(parent);return;}minecraft.setScreen(new UiConfirmDialog(yes->minecraft.setScreen(yes?parent:this),Component.literal("Отменить изменения?"),Component.literal("Введённый текст не будет сохранён.")));}
 public void receiveCommunity(JsonObject j){if(!(parent instanceof TaskScreen task)||!busy||!task.matches(j))return;task.receiveCommunity(j);busy=false;if(j.has("error")){error=Json.opt(j,"text","Не удалось сохранить изменения");rebuildWidgets();}else minecraft.setScreen(parent);}
 @Override public void tick(){if(validationDirty){validationDirty=false;rebuildWidgets();}if(busy&&System.currentTimeMillis()-sentAt>=15000){if(parent instanceof TaskScreen task)task.tick();busy=false;uncertain=true;error="Ответ не получен. Вернитесь к задаче и нажмите «Повторить». Введённый текст сохранён.";rebuildWidgets();}}
 @Override public boolean keyPressed(int key,int scan,int modifiers){if(UiKeyboard.submit(this,key,modifiers)){save();return true;}return super.keyPressed(key,scan,modifiers);}
 @Override public boolean isPauseScreen(){return false;}
}
