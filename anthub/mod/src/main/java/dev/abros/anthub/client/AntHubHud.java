package dev.abros.anthub.client;

import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Connection lifecycle and data delivery; renderer and preferences have no network side effects. */
final class AntHubHud {
 static JsonObject snapshot=new JsonObject();static final HudNoticeQueue QUEUE=new HudNoticeQueue();
 private static final KeyMapping INTERACT=new KeyMapping("key.anthub.hudInteract",GLFW.GLFW_KEY_UNKNOWN,"key.categories.anthub");
 private static Object connection;private static long nextPoll,sequence=-1,pendingAt;private static String pending="";
 private static final Set<String> readRequests=new HashSet<>();private static final Deque<String> readIds=new ArrayDeque<>();
 private static JsonObject acknowledgement;private static long acknowledgeAt;private static int acknowledgeRetries;
 private static long restartWarning;private static int lastSoundTick;private static final Set<String> DEFAULT_EVENTS=Set.of("invite","apply","application","response","cancel","reschedule","reply","workAssign","workDue","reminder","server","important","role","plusRemoveMember","plusEventInvite");
 static void install(IEventBus bus){bus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e)->e.register(INTERACT));
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Post e)->{if(Minecraft.getInstance().screen==null)render(e.getGuiGraphics());});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Post e)->{if(!(e.getScreen() instanceof HudInteractionScreen))render(e.getGuiGraphics());});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre e)->{if(e.getButton()==0&&HudRenderer.click(e.getMouseX(),e.getMouseY(),false))e.setCanceled(true);});
 }
 static void tick(){var mc=Minecraft.getInstance();Object current=mc.getConnection();if(current!=connection){connection=current;snapshot=new JsonObject();QUEUE.clear();sequence=-1;pending="";nextPoll=0;readRequests.clear();readIds.clear();acknowledgement=null;restartWarning=0;lastSoundTick=-1000;}
  if(!ServerMenuClient.available()||mc.player==null)return;long now=net.minecraft.Util.getMillis();
  while(INTERACT.consumeClick())if(mc.screen==null)mc.setScreen(new HudInteractionScreen(false,null));
  // Read acknowledgements are serialized and scoped to this connection.
  if(readRequests.isEmpty()&&!readIds.isEmpty()){var j=new JsonObject();String request=UUID.randomUUID().toString();j.addProperty("request",request);j.addProperty("action","community");j.addProperty("section","notifications");j.addProperty("op","read");j.addProperty("id",readIds.removeFirst());j.addProperty("exact",true);readRequests.add(request);acknowledgement=MenuCommands.prepare(j,System.currentTimeMillis());acknowledgeAt=now;acknowledgeRetries=0;ServerMenuClient.request(acknowledgement);}
  if(acknowledgement!=null&&now-acknowledgeAt>15000){if(acknowledgeRetries++<2){acknowledgeAt=now;ServerMenuClient.request(acknowledgement);}else{readRequests.clear();acknowledgement=null;HudSettings.INSTANCE.error="Не удалось отметить уведомление прочитанным";}}
  if(!ServerMenuClient.supports("hud"))return;
  if(!pending.isEmpty()&&now-pendingAt>15000){pending="";nextPoll=now+5000;}
  if(pending.isEmpty()&&now>=nextPoll){var j=new JsonObject();pending=UUID.randomUUID().toString();pendingAt=now;j.addProperty("request",pending);j.addProperty("action","community");j.addProperty("op","hud");j.addProperty("section","home");j.addProperty("pin",HudSettings.INSTANCE.pin());if(sequence>=0)j.addProperty("since",sequence);ServerMenuClient.requestBackground(j);nextPoll=now+10000;}
  long end=ServerMenuClient.state.has("restartAt")?ServerMenuClient.state.get("restartAt").getAsLong():0;if(end>System.currentTimeMillis()&&end-System.currentTimeMillis()<=60000&&end!=restartWarning){restartWarning=end;offer(new HudNoticeQueue.Notice("","server","","server","Скорый перезапуск","Сохраните работу: "+relative(end),HudNoticeQueue.Priority.URGENT));}
 }
 static void refresh(){nextPoll=0;}
 static boolean receive(JsonObject j){String request=Json.opt(j,"request","");if(readRequests.remove(request)){acknowledgement=null;if(!Json.opt(j,"kind","").equals("community"))HudSettings.INSTANCE.error="Не удалось отметить уведомление прочитанным";return true;}if(!request.equals(pending)||pending.isEmpty())return false;pending="";
  if(!Json.opt(j,"kind","").equals("hud")){nextPoll=net.minecraft.Util.getMillis()+30000;return true;}
  boolean initial=sequence<0;snapshot=j.deepCopy();sequence=j.get("sequence").getAsLong();if(initial){int count=j.get("unread").getAsInt();if(count>0)offer(new HudNoticeQueue.Notice("","notifications","","important","Непрочитанные: "+count,"Откройте центр уведомлений",HudNoticeQueue.Priority.IMPORTANT));}else{
   var prefs=j.getAsJsonObject("preferences");boolean snoozed=prefs.has("snoozeUntil")&&prefs.get("snoozeUntil").getAsLong()>System.currentTimeMillis();for(var element:j.getAsJsonArray("notices")){var n=element.getAsJsonObject();String section=Json.opt(n,"section","notifications");if(n.get("read").getAsBoolean()||snoozed||prefs.has("muted")&&prefs.getAsJsonArray("muted").contains(new JsonPrimitive(section)))continue;String event=Json.opt(n,"event","activity");var priority=switch(Json.opt(n,"priority","ordinary")){case "urgent"->HudNoticeQueue.Priority.URGENT;case "important"->HudNoticeQueue.Priority.IMPORTANT;default->HudNoticeQueue.Priority.ORDINARY;};if(HudDeliveryPolicy.allowed(HudSettings.INSTANCE.muted,section,event,priority!=HudNoticeQueue.Priority.ORDINARY||defaultEvent(event)))enqueue(new HudNoticeQueue.Notice(Json.str(n,"id"),Json.opt(n,"routeSection",section),Json.opt(n,"routeId",Json.opt(n,"target","")),event,Json.opt(n,"title","Уведомление"),Json.opt(n,"body",section.equals("home")?"Личные задачи":CommunityScreen.name(section)),priority,Json.opt(n,"routeGroup","")));}}
  if(j.get("more").getAsBoolean())nextPoll=0;return true;
 }
 static boolean defaultEvent(String event){return DEFAULT_EVENTS.contains(event);}
 static void offer(HudNoticeQueue.Notice n){if(!HudDeliveryPolicy.allowed(HudSettings.INSTANCE.muted,n.section(),n.event(),n.priority()!=HudNoticeQueue.Priority.ORDINARY||defaultEvent(n.event())))return;enqueue(n);}
 private static void enqueue(HudNoticeQueue.Notice n){if(!ServerMenuClient.enabled(0))return;var settings=HudSettings.INSTANCE;QUEUE.add(n,net.minecraft.Util.getMillis(),settings.toastCount);var mc=Minecraft.getInstance();if(ServerMenuClient.enabled(1)&&mc.player!=null&&mc.player.tickCount-lastSoundTick>20){mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,.15f,1.2f);lastSoundTick=mc.player.tickCount;}}
 static void acknowledge(List<String> ids){for(String id:ids)if(!readIds.contains(id)&&readIds.size()<200)readIds.addLast(id);}
 static boolean attention(){long now=System.currentTimeMillis(),restart=ServerMenuClient.state.has("restartAt")?ServerMenuClient.state.get("restartAt").getAsLong():0;if(ServerMenuClient.enabled(2)&&restart>now&&restart-now<=60000)return true;if(snapshot.has("tasks")&&!snapshot.getAsJsonArray("tasks").isEmpty()){var task=snapshot.getAsJsonArray("tasks").get(0).getAsJsonObject();long due=task.has("dueAt")?task.get("dueAt").getAsLong():0;return due>0&&due<now;}return false;}
 static boolean widgetVisible(){var mc=Minecraft.getInstance();var s=HudSettings.INSTANCE;if(!s.enabled&&!attention()||mc.options.hideGui||mc.player==null||!ServerMenuClient.available())return false;if(mc.screen instanceof HudInteractionScreen)return true;if(UiTheme.owns(mc.screen))return false;if(mc.getDebugOverlay().showDebugScreen()&&!s.debug)return false;return mc.screen==null||mc.screen instanceof ChatScreen&&s.chat||mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen&&s.inventory||mc.screen instanceof PauseScreen&&s.pause;}
 static void render(GuiGraphics g){var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;HudRenderer.render(g,widgetVisible(),false);}
 static String relative(long at){long delta=at-System.currentTimeMillis(),minutes=Math.abs(delta)/60000;if(delta<0)return minutes<60?"Просрочено на "+minutes+" мин":"Просрочено";if(minutes<60)return "Через "+Math.max(1,minutes)+" мин";if(minutes<1440)return "Через "+(minutes/60)+" ч";return java.time.Instant.ofEpochMilli(at).atZone(AccessibilityScreen.zone()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"));}
 static void open(String section,String id,String group){var mc=Minecraft.getInstance();if(section.equals("notifications")||section.isEmpty()){mc.setScreen(new NotificationPopup(null));return;}if(section.equals("home")&&!id.isEmpty()||section.equals("tasks")){mc.setScreen(new TaskScreen(null,group,id));return;}if(section.equals("server")){mc.setScreen(new ServerInfoScreen(null));return;}if(section.equals("help")&&!id.isEmpty()){FeatureListScreen.openReport(null,id);return;}mc.setScreen(new CommunityScreen(null,section,id));}
}
