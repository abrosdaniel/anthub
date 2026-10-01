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
 static List<String> sections(){var keys=new ArrayList<String>();var config=ServerMenuClient.state.has("communityConfig")?ServerMenuClient.state.getAsJsonObject("communityConfig"):new JsonObject();for(String key:List.of("home","players","tasks","board","groups","events","polls","ideas","info","help","admin")){if(!Set.of("tasks").contains(key)&&ServerMenuClient.state.has("features")&&!ServerMenuClient.state.getAsJsonArray("features").contains(new JsonPrimitive(key)))continue;if(key.equals("admin")&&!ServerMenuClient.staff())continue;if(config.has("sections")&&!Set.of("tasks","info","help","admin").contains(key)&&!config.getAsJsonArray("sections").contains(new JsonPrimitive(key)))continue;keys.add(key);}return keys;}
 static void configure(Button button,String key){if(key.equals("tasks")&&!TaskScreen.available()){button.active=false;button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Личные задачи требуют AntHub 3.3.0 на клиенте и сервере.")));}}
 void build(String current,Consumer<Button> add){var keys=sections();int shown=Math.max(1,(host.height-112)/24);offset=Math.max(0,Math.min(offset,Math.max(0,keys.size()-shown)));for(int i=offset;i<Math.min(keys.size(),offset+shown);i++){String key=keys.get(i);String label=CommunityScreen.name(key);var button=new SidebarButton(8,42+(i-offset)*24,left()-20,label,current.equals(key),()->navigate(key));configure(button,key);add.accept(button);}if(!ServerMenuClient.state.has("features")||ServerMenuClient.state.getAsJsonArray("features").contains(new JsonPrimitive("notifications")))add.accept(Button.builder(Component.literal(host.width<420?"Входящие":"Уведомления"),b->Minecraft.getInstance().setScreen(new NotificationPopup(host))).bounds(Math.max(left(),host.width-(host.width<420?88:150)),12,host.width<420?68:130,20).build());}
 private void navigate(String key){UiNavigation.open(host,key);}

 boolean scroll(double x,double dy){if(x<0||x>=left()||dy==0)return false;offset=Math.max(0,offset+(dy<0?1:-1));return true;}
}
