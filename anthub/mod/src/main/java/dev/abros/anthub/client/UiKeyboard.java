package dev.abros.anthub.client;
import java.util.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
/** Visual Tab order, bounded focus memory, and restoration after a child dialog. */
final class UiKeyboard {
 private record Focus(Class<?> type,String label,int cursor){}
 private static final Map<Screen,Focus> saved=new WeakHashMap<>();
 static void clear(){saved.clear();}
 static void remember(Screen screen){if(!UiTheme.owns(screen)||!(screen.getFocused() instanceof AbstractWidget w))return;saved.put(screen,new Focus(w.getClass(),w.getMessage().getString(),w instanceof EditBox e?e.getCursorPosition():-1));}
 static void initialized(ScreenEvent.Init.Post event){restore(event.getScreen());}
 static void restore(Screen screen){if(!UiTheme.owns(screen))return;var focus=saved.get(screen);if(focus==null)return;for(var child:screen.children())if(child instanceof AbstractWidget w&&w.visible&&w.active&&w.getClass()==focus.type()&&w.getMessage().getString().equals(focus.label())){screen.setFocused(w);if(w instanceof EditBox edit&&focus.cursor()>=0)edit.setCursorPosition(Math.min(focus.cursor(),edit.getValue().length()));return;}}
 static boolean submit(Screen screen,int key,int modifiers){return (key==257||key==335)&&(screen.getFocused() instanceof EditBox||screen.getFocused() instanceof MultiLineEditBox&&(modifiers&2)!=0);}
 static boolean tab(Screen screen,int key,int modifiers){if(key!=258||!UiTheme.owns(screen))return false;var controls=screen.children().stream().filter(c->c instanceof AbstractWidget w&&w.visible&&w.active).map(c->(AbstractWidget)c).sorted(Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX)).toList();if(controls.isEmpty())return false;int at=controls.indexOf(screen.getFocused()),step=(modifiers&1)!=0?-1:1;screen.setFocused(controls.get(at<0?step>0?0:controls.size()-1:Math.floorMod(at+step,controls.size())));return true;}
 private UiKeyboard(){}
}
