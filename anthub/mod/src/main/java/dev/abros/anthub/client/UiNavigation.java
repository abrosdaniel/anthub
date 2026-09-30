package dev.abros.anthub.client;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
/** Bounded navigation memory, discarded on disconnect or permission changes. */
final class UiNavigation {
 private static final Map<String,Screen> pages=new LinkedHashMap<>();
 static void clear(){pages.clear();}
 static void open(Screen from,String key){var mc=Minecraft.getInstance();if(from instanceof CommunityScreen menu&&menu.itemId.isEmpty())pages.put(menu.section,from);else if(from instanceof TaskScreen tasks&&tasks.personal())pages.put("tasks",from);else if(from instanceof FeatureListScreen list&&list.navigationPage())pages.put("players",from);
  if(key.equals("notifications")){mc.setScreen(new NotificationPopup(from));return;}if(key.equals("tasks")&&!TaskScreen.available())return;if(from instanceof CommunityScreen menu&&!menu.leavePage()||from instanceof TaskScreen tasks&&!tasks.leavePage())return;if(from instanceof FeatureListScreen list)list.leavePage();
  Screen next=pages.get(key);if(next==null){if(key.equals("players")){FeatureListScreen.open(null,"players");next=mc.screen;}else{next=key.equals("tasks")?new TaskScreen(null,"",""):key.equals("info")?new ServerInfoScreen(null):Set.of("help","admin").contains(key)?new ServerMenuScreen(null,key):new CommunityScreen(null,key,"");mc.setScreen(next);}pages.put(key,next);}else{mc.setScreen(next);if(next instanceof CommunityScreen menu)menu.invalidate();else if(next instanceof TaskScreen tasks)tasks.invalidate(tasks.personal()?"home":"groups","");else if(next instanceof FeatureListScreen list)list.invalidate();}
 }
}
