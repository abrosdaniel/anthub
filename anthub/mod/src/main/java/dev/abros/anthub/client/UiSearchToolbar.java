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
  int fw=Math.min(90,Math.max(60,w/4));var input=query(font,new dev.abros.anthub.core.NativeLayout.Box(x,y,w-fw-6,20),"Поиск",query,add,change,search,enabled);
  var labels=List.of("Все","Мои","Участвую");var f=UiActions.button(Component.literal(labels.get(filter)+" ▾"),UiActions.Tone.NORMAL,"",b->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(screen,"Показать",labels,n->select.accept(n),b).current(filter))).bounds(x+w-fw,y,fw,20).build();f.active=enabled;add.accept(f);
  var sorts=List.of("По умолчанию","Сначала новые","Сначала старые");var keys=List.of("default","recent","oldest");int selected=Math.max(0,keys.indexOf(sort));int sw=Math.min(134,w/2);
  var sorting=UiActions.button(Component.literal(sorts.get(selected)+" ▾"),UiActions.Tone.NORMAL,"",b->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(screen,"Порядок записей",sorts,n->order.accept(keys.get(n)),b).current(selected))).bounds(x,y+26,sw,20).build();sorting.active=enabled;add.accept(sorting);
  if(!query.isBlank()||filter>0||archived||selected>0){String label=(query.isBlank()?"":query+" · ")+(filter>0?labels.get(filter)+" · ":"")+(archived?"Архив · ":"")+"Сбросить ×";var chip=UiActions.button(Component.literal(UiKit.fit(font,label,Math.max(12,w-sw-16))),UiActions.Tone.NORMAL,"",b->{var names=new java.util.ArrayList<String>();var resets=new java.util.ArrayList<Runnable>();if(!query.isBlank()){names.add("Убрать поиск: "+query);resets.add(()->{change.accept("");search.run();});}if(filter>0){names.add("Показать все записи");resets.add(()->select.accept(0));}if(selected>0){names.add("Сбросить порядок");resets.add(()->order.accept("default"));}names.add("Сбросить все условия");resets.add(clear);net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(screen,"Активные условия",names,n->resets.get(n).run(),b));}).bounds(x+sw+6,y+26,Math.max(24,w-sw-6),20).tooltip(Tooltip.create(Component.literal(label))).build();chip.active=enabled;add.accept(chip);}
  return input;
 }
 static UiEditBox query(Font font,dev.abros.anthub.core.NativeLayout.Box area,String label,String value,Consumer<AbstractWidget> add,Consumer<String> change,Runnable submit,boolean enabled){
  int find=Math.min(48,area.width()/3);var input=UiFields.text(font,area.x(),area.y(),Math.max(1,area.width()-find-6),area.height(),label,label,100,value,change);input.active=enabled;add.accept(input);
  var go=UiActions.button(Component.literal("Найти"),UiActions.Tone.NORMAL,UiIcons.SEARCH,b->submit.run()).bounds(area.right()-find,area.y(),find,area.height()).build();go.active=enabled;add.accept(go);return input;
 }

}
