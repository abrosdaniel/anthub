package dev.abros.anthub.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.*;
/** Shared search, ownership filter, chronological ordering and explicit removable filter summary. */
final class UiSearchToolbar {
 static UiEditBox build(Screen screen,Font font,int x,int y,int w,String query,int filter,String sort,boolean archived,Consumer<AbstractWidget> add,Consumer<String> change,Runnable search,IntConsumer select,Consumer<String> order,Runnable clear,boolean enabled){
  int fw=Math.min(90,Math.max(60,w/4)),find=48;var input=new UiEditBox(font,x,y,Math.max(24,w-fw-find-12),20,Component.literal("Поиск"));input.setMaxLength(100);input.setHint(Component.literal("Поиск"));input.setValue(query);input.setResponder(change);add.accept(input);
  var go=Button.builder(Component.literal("Найти"),b->search.run()).bounds(x+w-fw-find-6,y,find,20).build();go.active=enabled;add.accept(go);
  var labels=List.of("Все","Мои","Участвую");var f=Button.builder(Component.literal(labels.get(filter)+" ▾"),b->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(screen,"Показать",labels,n->select.accept(n),b).current(filter))).bounds(x+w-fw,y,fw,20).build();f.active=enabled;add.accept(f);
  var sorts=List.of("По умолчанию","Сначала новые","Сначала старые");var keys=List.of("default","recent","oldest");int selected=Math.max(0,keys.indexOf(sort));int sw=Math.min(134,w/2);
  var sorting=Button.builder(Component.literal(sorts.get(selected)+" ▾"),b->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(screen,"Порядок записей",sorts,n->order.accept(keys.get(n)),b).current(selected))).bounds(x,y+26,sw,20).build();sorting.active=enabled;add.accept(sorting);
  if(!query.isBlank()||filter>0||archived||selected>0){String label=(query.isBlank()?"":query+" · ")+(filter>0?labels.get(filter)+" · ":"")+(archived?"Архив · ":"")+"Сбросить ×";var chip=Button.builder(Component.literal(UiKit.fit(font,label,Math.max(12,w-sw-16))),b->clear.run()).bounds(x+sw+6,y+26,Math.max(24,w-sw-6),20).tooltip(Tooltip.create(Component.literal(label))).build();chip.active=enabled;add.accept(chip);}
  return input;
 }
}
