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
 private int offset;private final Screen host;private UiWorkspace workspace;
 MenuSidebar(Screen host){this.host=host;}
 int right(){return host.width-UiWorkspace.OUTER_X;}
 int left(){return UiWorkspace.fit(host.width,host.height).page().x();}
 static List<String> sections(){var keys=new ArrayList<String>();var config=ServerMenuClient.state.has("communityConfig")?ServerMenuClient.state.getAsJsonObject("communityConfig"):new JsonObject();for(String key:List.of("home","players","tasks","board","groups","events","polls","ideas","info","help","admin")){if(!Set.of("tasks").contains(key)&&ServerMenuClient.state.has("features")&&!ServerMenuClient.state.getAsJsonArray("features").contains(new JsonPrimitive(key)))continue;if(key.equals("admin")&&!ServerMenuClient.staff())continue;if(config.has("sections")&&!Set.of("tasks","info","help","admin").contains(key)&&!config.getAsJsonArray("sections").contains(new JsonPrimitive(key)))continue;keys.add(key);}return keys;}
 static void configure(Button button,String key){if(key.equals("tasks")&&!TaskScreen.available()){button.active=false;button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Личные задачи требуют AntHub 3.3.0 на клиенте и сервере.")));}}
 void build(String current,Consumer<Button> add){workspace=UiWorkspace.fit(host.width,host.height);buildFrame(current,add);}
 static String inboxLabel(int width){int unread=ServerMenuClient.state.has("unread")?ServerMenuClient.state.get("unread").getAsInt():0;return (width<420?"Входящие":"Уведомления")+(unread>0?" ("+unread+")":"");}
 private void buildFrame(String current,Consumer<Button> add){var keys=sections();var area=workspace.sidebar();int shown=Math.max(1,area.height()/24);offset=Math.max(0,Math.min(offset,Math.max(0,keys.size()-shown)));for(int n=offset;n<Math.min(keys.size(),offset+shown);n++){String key=keys.get(n);var button=new SidebarButton(area.x(),area.y()+(n-offset)*24,area.width(),CommunityScreen.name(key),current.equals(key),()->navigate(key));configure(button,key);add.accept(button);}if(!ServerMenuClient.state.has("features")||ServerMenuClient.state.getAsJsonArray("features").contains(new JsonPrimitive("notifications"))){var header=workspace.header();add.accept(UiActions.button(Component.literal(inboxLabel(host.width)),UiActions.Tone.NORMAL,"",b->Minecraft.getInstance().setScreen(new NotificationPopup(host))).bounds(header.right()-(host.width<420?68:130),header.y(),host.width<420?68:130,20).build());}}
 private void navigate(String key){UiNavigation.open(host,key);}

 boolean scroll(double x,double y,double dy){if(workspace!=null&&(y<workspace.sidebar().y()||y>=workspace.sidebar().bottom()))return false;return scroll(x,dy);}
 void drawFrame(net.minecraft.client.gui.GuiGraphics g){if(workspace==null)return;var area=workspace.sidebar();int count=sections().size(),shown=Math.max(1,area.height()/24);if(count<=shown)return;int h=Math.max(12,area.height()*shown/count),y=area.y()+(area.height()-h)*offset/(count-shown);g.fill(area.right()+4,area.y(),area.right()+6,area.bottom(),UiPalette.scrollTrack());g.fill(area.right()+4,y,area.right()+6,y+h,UiPalette.scrollThumb());}
 boolean scroll(double x,double dy){if(dy==0||workspace!=null&&(x<workspace.sidebar().x()||x>=workspace.sidebar().right())||workspace==null&&(x<0||x>=left()))return false;offset=Math.max(0,offset+(dy<0?1:-1));return true;}
}
