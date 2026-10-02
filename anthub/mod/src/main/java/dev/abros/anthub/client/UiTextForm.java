package dev.abros.anthub.client;

import dev.abros.anthub.core.NativeLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Consumer;
import static dev.abros.anthub.core.NativeLayout.Track.*;

/** Reusable native title/description form. State and persistence belong to its controller. */
final class UiTextForm {
 record Field(String label,String hint,int limit,String value,Consumer<String> changed) {}
 static List<AbstractWidget> compose(Font font,NativeLayout.Box area,Field title,Field description,
                                    boolean editable,boolean canSave,String saveLabel,Runnable save,Runnable cancel,String titleIssue) {
  var slots=NativeLayout.column(area,6,fixed(titleIssue.isBlank()?20:34),flex(1),fixed(10),fixed(20));
  var titleBox=slots.get(0);
  var input=UiFields.text(font,titleBox.x(),titleBox.y(),titleBox.width()-8,20,title.label(),title.hint(),title.limit(),title.value(),title.changed());
  input.active=editable;UiFields.issue(input,titleIssue);
  var descBox=slots.get(1);
  var text=UiFields.multiline(font,descBox.x(),descBox.y(),descBox.width()-8,descBox.height(),description.label(),description.hint(),description.limit(),description.value(),description.changed());
  text.active=editable;
  var buttons=UiActions.row(slots.get(3),ignored->{},new NativeLayout.Track[]{fixed(104),fixed(80)},
   UiActions.primary(saveLabel,save,canSave).withIcon(UiIcons.SAVE).because(editable?"Сначала проверьте результат предыдущей отправки":"Дождитесь завершения сохранения"),UiActions.action("Отмена",cancel,editable).because("Дождитесь завершения сохранения"));
  return List.of(input,text,buttons.get(0),buttons.get(1));
 }
 private UiTextForm() {}
}
