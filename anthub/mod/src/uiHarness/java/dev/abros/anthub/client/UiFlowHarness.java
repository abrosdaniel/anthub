package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Behavioral checks for typed actions, keyboard navigation, validation and repeated search responses. */
final class UiFlowHarness {
 static void verify(){
  var mc=Minecraft.getInstance();var previous=mc.screen;var transport=ServerMenuClient.previewTransport;
  try{
   UiGeometryHarness.verifyLayouts();
   var typed=UiActions.button(Component.literal("Любое название"),UiActions.Tone.DANGER,UiIcons.DELETE,b->{}).bounds(0,0,80,20).build();typed.setMessage(Component.literal("Переименовано"));if(UiActions.style(typed).tone()!=UiActions.Tone.DANGER)throw new IllegalStateException("Action role depends on text");
   var preset=new JsonObject();preset.addProperty("title","");var editor=new TaskEditScreen(previous,"Проверка формы",List.of(new CommunityScreen.Field("title","Название",100)),preset,q->{throw new IllegalStateException("Invalid form sent a request");});mc.setScreen(editor);
   var field=editor.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();editor.setFocused(field);
   UiKeyboard.tab(editor,258,0);if(editor.getFocused()==field)throw new IllegalStateException("Tab did not leave input");UiKeyboard.tab(editor,258,1);if(editor.getFocused()!=field)throw new IllegalStateException("Shift Tab did not return to input");
   field.setValue("Сохранённый текст");field.setCursorPosition(4);editor.setFocused(field);mc.setScreen(new ChoicePopup(editor,"Выбор",List.of("Один","Два"),n->{}));mc.screen.onClose();if(!(editor.getFocused() instanceof EditBox restored)||restored.getCursorPosition()!=4||!restored.getValue().equals("Сохранённый текст"))throw new IllegalStateException("Dialog return lost focus or cursor");
   ((EditBox)editor.getFocused()).setValue("");for(var child:editor.children())if(child instanceof Button b&&b.getMessage().getString().equals("Сохранить")){b.onPress();break;}if(!(editor.getFocused() instanceof EditBox))throw new IllegalStateException("Validation did not focus invalid field");
   var packet=new JsonObject[]{null};ServerMenuClient.previewTransport=q->packet[0]=q;var search=new GlobalSearchScreen(previous);mc.setScreen(search);
   for(int pass=0;pass<3;pass++){
    var input=search.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();input.setValue("Поиск");search.setFocused(input);search.keyPressed(257,0,0);
    if(packet[0]==null)throw new IllegalStateException("Search was not sent");var response=new JsonObject();response.addProperty("request",Json.str(packet[0],"request"));if(pass==2){response.addProperty("error",true);response.addProperty("text","Проверка ошибки");}else response.add("entries",new JsonArray());search.receiveCommunity(response);
    if(search.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("Найти")).map(c->(Button)c).noneMatch(b->b.active))throw new IllegalStateException("Search controls stayed disabled after repeated response");
   }
   var latest=new GlobalSearchScreen(previous);mc.setScreen(latest);var input=latest.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();input.setValue("Первый");latest.setFocused(input);latest.keyPressed(257,0,0);String old=Json.str(packet[0],"request");input.setValue("Второй");latest.keyPressed(257,0,0);String current=Json.str(packet[0],"request");if(old.equals(current))throw new IllegalStateException("New search reused cancelled correlation");var stale=new JsonObject();stale.addProperty("request",old);stale.addProperty("error",true);stale.addProperty("text","Stale response");latest.receiveCommunity(stale);var ready=new JsonObject();ready.addProperty("request",current);ready.add("entries",new JsonArray());latest.receiveCommunity(ready);if(latest.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("Найти")).map(c->(Button)c).noneMatch(b->b.active))throw new IllegalStateException("Latest search failed after stale response");
   System.out.println("ANTHUB_FLOW_OK: typed actions, Tab, focus/cursor return, validation, repeated search/error responses");
  }finally{ServerMenuClient.previewTransport=transport;mc.setScreen(previous);}
 }
 private UiFlowHarness(){}
}
