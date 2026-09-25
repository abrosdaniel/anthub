package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.*;
@EventBusSubscriber(modid="anthub",value=Dist.CLIENT)
public final class CommunityPlusUiHarness {
 private static int step;private static long at;private static boolean started;
 @SubscribeEvent public static void render(ScreenEvent.Render.Post event){String output=System.getenv("ANTHUB_PLUS_UI");if(output==null||step<0)return;var mc=Minecraft.getInstance();if(!started){if(!(event.getScreen() instanceof TitleScreen))return;started=true;at=System.currentTimeMillis()+1500;return;}if(System.currentTimeMillis()<at)return;at=System.currentTimeMillis()+900;
  try{if(step>0){var dir=new java.io.File(output);dir.mkdirs();net.minecraft.client.Screenshot.grab(dir,String.format("plus-%02d.png",step),mc.getMainRenderTarget(),message->{});}
   switch(step++){
    case 0->{mc.options.guiScale().set(1);mc.resizeDisplay();CommunityUiHarness.open("normal");}
    case 1->{bounds();var group=new CommunityScreen(mc.screen,"groups",new UUID(0,11).toString());group.groupTab="tasks";mc.setScreen(group);}
    case 2->{bounds();((CommunityScreen)mc.screen).groupTab="places";((CommunityScreen)mc.screen).refreshUi();}
    case 3->{bounds();var parent=new CommunityScreen(mc.screen,"events","");var fields=new ArrayList<CommunityScreen.Field>();fields.add(new CommunityScreen.Field("title","Название",100));fields.add(new CommunityScreen.Field("description","Описание",1500));fields.add(new CommunityScreen.Field("startsAt","Начало",30));var preset=new JsonObject();CommunityTools.creation("events",fields,preset);mc.setScreen(new CommunityForm(parent,"Создать событие",fields,preset,j->{}));}
    case 4->{bounds();mc.screen.mouseScrolled(mc.screen.width/2,mc.screen.height/2,0,-10);}
    case 5->{bounds();mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new DateTimeScreen(mc.screen,"",value->{}));}
    case 6->{bounds();overlaps();mc.options.guiScale().set(1);mc.resizeDisplay();mc.setScreen(new PersonalProfileScreen(mc.screen));}
    case 7->{bounds();overlaps();PersonalProfileScreen.ignores(mc.screen,"Example");}
    case 8->{bounds();mc.options.guiScale().set(3);mc.resizeDisplay();CommunityUiHarness.open("normal");}
    case 9->{bounds();mc.options.guiScale().set(4);mc.resizeDisplay();CommunityUiHarness.open("long");}
    case 10->{bounds();mc.options.guiScale().set(2);mc.resizeDisplay();CommunityUiHarness.open("normal");mc.setScreen(new CommunityPreferences(mc.screen,new JsonObject()));}
    case 11->{bounds();overlaps();}
    case 12->{mc.options.guiScale().set(1);mc.resizeDisplay();var method=CommunityUiHarness.class.getDeclaredMethod("player");method.setAccessible(true);var player=(JsonObject)method.invoke(null);player.addProperty("uuid",new UUID(1,123).toString());mc.setScreen(new PlayerActionsScreen(null,player,ServerMenuClient.state.getAsJsonArray("actions")));}
    case 13->{bounds();overlaps();for(var child:mc.screen.children())if(child instanceof Button b&&b.getMessage().getString().equals("Модерация…")){b.onPress();break;}}
    case 14->{bounds();overlaps();mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new PersonalProfileScreen(null));}
    case 15->{bounds();overlaps();mc.setScreen(new CoreVersionsPopup(new TitleScreen()));}
    case 16->{bounds();overlaps();boolean installed=false;for(var child:mc.screen.children())if(child instanceof Button b){String text=b.getMessage().getString();if(text.startsWith("✓ ")){if(!text.contains(dev.abros.anthub.AntHub.VERSION))throw new IllegalStateException("Wrong installed version");installed=true;}}if(!installed)throw new IllegalStateException("Installed version missing");}
    case 17->{mc.options.guiScale().set(1);mc.resizeDisplay();CommunityUiHarness.open("normal");}
    case 18->{boolean opened=false;for(var child:mc.screen.children())if(child instanceof CommunityCard card&&card.getX()>mc.screen.width-240){card.onPress();opened=true;break;}if(!opened)throw new IllegalStateException("Home group missing");}
    case 19->{bounds();overlaps();var screen=(CommunityScreen)mc.screen;screen.groupTab="tasks";screen.refreshUi();}
    case 20->{bounds();overlaps();var screen=(CommunityScreen)mc.screen;var items=screen.data.getAsJsonObject("detail").getAsJsonArray("groupItems");if(!items.isEmpty()){var copy=items.get(0).deepCopy().getAsJsonObject();copy.addProperty("id",UUID.randomUUID().toString());copy.addProperty("title","Вторая задача");items.add(copy);}screen.refreshUi();}
    case 21->{bounds();overlaps();mc.screen.mouseScrolled(mc.screen.width-60,mc.screen.height/2,0,-4);}
    case 22->{bounds();mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new AccessibilityScreen(mc.screen));}
    case 23->{bounds();overlaps();boolean found=false;for(var child:mc.screen.children())if(child instanceof Button b&&b.getMessage().getString().startsWith("Прозрачные панели:")){String before=b.getMessage().getString();b.onPress();for(var after:mc.screen.children())if(after instanceof Button a&&a.getMessage().getString().startsWith("Прозрачные панели:")){if(a.getMessage().getString().equals(before))throw new IllegalStateException("Transparency label unchanged");a.onPress();break;}found=true;break;}if(!found)throw new IllegalStateException("Transparency control missing");}
    case 24->{mc.options.guiScale().set(1);mc.resizeDisplay();mc.setScreen(new ServerMenuScreen(null,"admin"));}
    case 25->{bounds();overlaps();for(var child:mc.screen.children())if(child instanceof Button b&&b.getMessage().getString().equals("Сообщество"))throw new IllegalStateException("Duplicate admin navigation remains");}
    case 26->{bounds();overlaps();var group=new CommunityScreen(null,"groups",new UUID(0,11).toString());group.groupTab="requests";mc.setScreen(group);}
    case 27->{bounds();overlaps();var notice=new JsonObject();notice.addProperty("kind","moderationVoteStatus");var vote=new JsonObject();vote.addProperty("id","test-notice");vote.addProperty("status","open");vote.addProperty("name","Example Player");vote.addProperty("reason","Нарушение правил сервера");vote.addProperty("endsAt",System.currentTimeMillis()+120000);notice.add("vote",vote);ServerMenuClient.receive(notice);mc.setScreen(new NotificationPopup(new CommunityScreen(null,"home","")));}
    case 28->{bounds();overlaps();boolean found=false;for(var child:mc.screen.children())if(child instanceof Button b&&b.getMessage().getString().contains("Example Player")){found=true;break;}if(!found)throw new IllegalStateException("Active vote notification missing");mc.options.guiScale().set(2);mc.resizeDisplay();}
    case 29->{bounds();overlaps();mc.setScreen(new CommunityScreen(null,"polls",""));}
    case 30->{bounds();overlaps();ServerMenuClient.moderationVote=new JsonObject();}
    case 31->{mc.options.guiScale().set(1);mc.resizeDisplay();var report=new JsonObject();report.addProperty("id","test-report");report.addProperty("player","Example Player");report.addProperty("message","Прошу проверить проблему. ".repeat(15));report.addProperty("status","open");report.addProperty("revision",1);mc.setScreen(new ReportDetailScreen(null,report,true));}
    case 32->{bounds();overlaps();mc.options.guiScale().set(2);mc.resizeDisplay();}
    case 33->{bounds();overlaps();}
    case 34->{CommunityUiHarness.open("normal");mc.setScreen(new CommunityScreen(null,"board",""));}
    case 35->{var screen=(CommunityScreen)mc.screen;var saved=screen.data.deepCopy();var widgets=new ArrayList<>(screen.children());var transport=ServerMenuClient.previewTransport;try{ServerMenuClient.previewTransport=q->{var response=saved.deepCopy();response.addProperty("request",Json.str(q,"request"));ServerMenuClient.receive(response);};for(int i=0;i<10;i++)screen.send("list",new JsonObject());if(!widgets.equals(screen.children()))throw new IllegalStateException("Unchanged responses rebuilt UI");var dirty=CommunityScreen.class.getDeclaredField("dirtyAt");dirty.setAccessible(true);dirty.setLong(screen,0);screen.invalidate("ideas","");if(dirty.getLong(screen)!=0)throw new IllegalStateException("Unrelated section caused request");saved.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("title","Изменённая запись");screen.send("list",new JsonObject());if(widgets.equals(screen.children()))throw new IllegalStateException("Changed response did not refresh UI");screen.busy=true;screen.refreshUi();screen.busy=false;var waitingWidgets=new ArrayList<>(screen.children());screen.send("list",new JsonObject());if(waitingWidgets.equals(screen.children()))throw new IllegalStateException("Busy controls not restored after unchanged response");System.out.println("ANTHUB_UNCHANGED_UI_OK: 10 identical responses, 0 rebuilds; changed response rebuilt");}finally{ServerMenuClient.previewTransport=transport;}}
    default->{System.out.println("ANTHUB_PLUS_UI_COMPLETE");if(System.getenv("ANTHUB_UI_KEEP_OPEN")!=null){step=-1;mc.options.guiScale().set(1);mc.resizeDisplay();CommunityUiHarness.open("normal");}else mc.stop();}
   }
  }catch(Throwable failure){failure.printStackTrace();step=-1;mc.stop();}
 }
 private static void bounds(){var screen=Minecraft.getInstance().screen;for(var child:screen.children())if(child instanceof AbstractWidget w&&w.visible){if(w.getX()<0||w.getY()<0||w.getRight()>screen.width||w.getBottom()>screen.height)throw new IllegalStateException("Widget outside screen: "+w.getMessage().getString()+" / "+screen.width+"x"+screen.height);}}
 private static void overlaps(){var screen=Minecraft.getInstance().screen;var boxes=new ArrayList<UiLayout.Rect>();for(var child:screen.children())if(child instanceof AbstractWidget w&&w.visible){var box=new UiLayout.Rect(w.getX(),w.getY(),w.getWidth(),w.getHeight());for(var prior:boxes)if(box.overlaps(prior))throw new IllegalStateException("Overlapping widget: "+w.getMessage().getString());boxes.add(box);}}
}
