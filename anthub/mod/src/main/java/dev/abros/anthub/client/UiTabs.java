package dev.abros.anthub.client;
import dev.abros.anthub.core.NativeLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.*;
/** One tab composition: equal tracks when labels fit, an anchored selector otherwise. */
final class UiTabs {
 static void build(Screen host,Font font,NativeLayout.Box area,List<String> labels,int selected,Consumer<AbstractWidget> add,IntConsumer choose,boolean enabled){
  if(labels.isEmpty())return;
  int current=Math.max(0,Math.min(selected,labels.size()-1));
  int cell=(area.width()-UiKit.GAP*(labels.size()-1))/labels.size();
  if(labels.stream().anyMatch(label->font.width(label)+16>cell)){
   var button=UiActions.button(Component.literal(labels.get(current)+" ▾"),UiActions.Tone.NORMAL,"",b->Minecraft.getInstance().setScreen(new ChoicePopup(host,"Раздел",labels,choose::accept,b).current(current))).bounds(area.x(),area.y(),area.width(),area.height()).build();button.active=enabled;add.accept(button);return;
  }
  var tracks=new NativeLayout.Track[labels.size()];java.util.Arrays.fill(tracks,NativeLayout.Track.flex(1));var boxes=NativeLayout.row(area,UiKit.GAP,tracks);
  for(int n=0;n<labels.size();n++){int index=n;var box=boxes.get(n);var button=new TabButton(box.x(),box.y(),box.width(),labels.get(n),n==current,()->choose.accept(index));button.active=enabled;add.accept(button);}
 }
 private UiTabs(){}
}
