package dev.abros.anthub.client;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Shared navigation for built-in server sections. */
final class MenuSidebar {
 private int offset;private final Screen host;
 MenuSidebar(Screen host){this.host=host;}
 int left(){return Math.min(146,Math.max(96,host.width/4))+12;}
 void build(String current,Consumer<Button> add){var keys=new ArrayList<String>();var config=ServerMenuClient.state.has("communityConfig")?ServerMenuClient.state.getAsJsonObject("communityConfig"):new JsonObject();for(String key:List.of("home","players","board","groups","events","polls","ideas","info","help","admin")){if(key.equals("admin")&&!ServerMenuClient.admin())continue;if(config.has("sections")&&!Set.of("info","help","admin").contains(key)&&!config.getAsJsonArray("sections").contains(new JsonPrimitive(key)))continue;keys.add(key);}int shown=Math.max(1,(host.height-82)/24);offset=Math.max(0,Math.min(offset,Math.max(0,keys.size()-shown)));for(int i=offset;i<Math.min(keys.size(),offset+shown);i++){String key=keys.get(i);String label=Minecraft.getInstance().font.plainSubstrByWidth(CommunityScreen.name(key),left()-28);var button=new SidebarButton(8,42+(i-offset)*24,left()-20,label,current.equals(key),()->navigate(key));add.accept(button);}add.accept(Button.builder(Component.literal("Настройки"),b->CommunityScreen.openSettings(host)).bounds(8,host.height-28,left()-20,20).build());}
 private void navigate(String key){var mc=Minecraft.getInstance();if(key.equals("players")){FeatureListScreen.open(null,"players");return;}if(key.equals("info")){mc.setScreen(new ServerInfoScreen(null));return;}if(Set.of("help","admin").contains(key)){mc.setScreen(new ServerMenuScreen(null,key));return;}mc.setScreen(new CommunityScreen(null,key,""));}
 boolean scroll(double x,double dy){if(x>=left())return false;offset=Math.max(0,offset+(dy<0?1:-1));return true;}
}
