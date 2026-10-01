package dev.abros.anthub.client;
import java.util.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
/** Bounded navigation memory, discarded on disconnect or permission changes. */
final class UiNavigation {
 private static final Map<String,Screen> pages=new LinkedHashMap<>();
 private static Screen outside;
 private static final Deque<Screen> history=new ArrayDeque<>();
 private static boolean returning;
 static void clear(){pages.clear();history.clear();outside=null;}
 static void open(Screen from,String key){var mc=Minecraft.getInstance();if(from instanceof CommunityScreen menu&&menu.itemId.isEmpty())pages.put(menu.section,from);else if(from instanceof TaskScreen tasks&&tasks.personal())pages.put("tasks",from);else if(from instanceof FeatureListScreen list&&list.navigationPage())pages.put("players",from);
  if(key.equals("notifications")){mc.setScreen(new NotificationPopup(from));return;}if(key.equals("tasks")&&!TaskScreen.available())return;if(from instanceof CommunityScreen menu&&!menu.leavePage()||from instanceof TaskScreen tasks&&!tasks.leavePage())return;if(from instanceof FeatureListScreen list)list.leavePage();
  Screen next=pages.get(key);if(next==null){if(key.equals("players")){FeatureListScreen.open(null,"players");next=mc.screen;}else{next=key.equals("tasks")?new TaskScreen(null,"",""):key.equals("info")?new ServerInfoScreen(null):Set.of("help","admin").contains(key)?new ServerMenuScreen(null,key):new CommunityScreen(null,key,"");mc.setScreen(next);}pages.put(key,next);}else{mc.setScreen(next);if(next instanceof CommunityScreen menu)menu.invalidate();else if(next instanceof TaskScreen tasks)tasks.invalidate(tasks.personal()?"home":"groups","");else if(next instanceof FeatureListScreen list)list.invalidate();}
 }
 static void opening(ScreenEvent.Opening event){
  var current=Minecraft.getInstance().screen;
  if(returning)return;
  if(UiTheme.owns(event.getNewScreen())){
   if(!UiTheme.owns(current)){outside=current;history.clear();}
   else if(current!=event.getNewScreen()){history.remove(event.getNewScreen());history.addLast(current);while(history.size()>32)history.removeFirst();}
  }
 }
 static boolean handle(Screen screen,int key){
  if(!UiTheme.owns(screen))return false;
  if(key==256){var client=Minecraft.getInstance();Screen destination=client.level!=null?null:outside;returning=true;try{screen.onClose();client.setScreen(destination);history.clear();}finally{returning=false;}return true;}
  if(key==259&&!(screen.getFocused() instanceof EditBox)&&!(screen.getFocused() instanceof MultiLineEditBox)){returning=true;try{screen.onClose();if(!UiTheme.owns(Minecraft.getInstance().screen)&&!history.isEmpty())Minecraft.getInstance().setScreen(history.removeLast());else history.remove(Minecraft.getInstance().screen);}finally{returning=false;}return true;}
  return false;
 }
 static void key(ScreenEvent.KeyPressed.Pre event){if(handle(event.getScreen(),event.getKeyCode()))event.setCanceled(true);}
}
