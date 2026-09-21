package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.*;
/** Opt-in fixture transport: exercises the real widgets without connecting to any server. */
@EventBusSubscriber(modid="anthub",value=Dist.CLIENT)
public final class CommunityUiHarness {
 private static final String ACTOR="00000000-0000-0000-0000-000000000001";
 private static String scenario="normal";
 private static int smokeStep;private static long smokeAt;private static boolean smokeStarted;
 @SubscribeEvent public static void smoke(ScreenEvent.Render.Post event){
  String output=System.getenv("ANTHUB_UI_SMOKE");if(output==null||smokeStep<0)return;var mc=Minecraft.getInstance();
  if(!smokeStarted){if(!(event.getScreen() instanceof TitleScreen))return;smokeStarted=true;if(System.getenv("ANTHUB_UI_FROM")!=null)smokeStep=Integer.parseInt(System.getenv("ANTHUB_UI_FROM"));smokeAt=System.currentTimeMillis()+1500;return;}
  if(System.currentTimeMillis()<smokeAt)return;smokeAt=System.currentTimeMillis()+900;
  try{
   if(smokeStep>0){var dir=new java.io.File(output);dir.mkdirs();net.minecraft.client.Screenshot.grab(dir,String.format("menu-%02d.png",smokeStep),mc.getMainRenderTarget(),message->{});}
   switch(smokeStep++){
    case 0 -> {mc.options.guiScale().set(1);mc.resizeDisplay();open("normal");}
    case 1 -> mc.setScreen(new CommunityScreen(null,"groups",""));
    case 2 -> clickCard();
    case 3 -> clickLabel("Участники");
    case 4 -> clickLabel("Заявки");
    case 5 -> clickLabel("⋯");
    case 6 -> mc.setScreen(new CommunityScreen(null,"events",""));
    case 7 -> clickCard();
    case 8 -> mc.setScreen(new CommunityScreen(null,"polls",""));
    case 9 -> clickCard();
    case 10 -> {for(var child:mc.screen.children())if(child instanceof PollOption option){option.onPress();break;}}
    case 11 -> clickLabel("Голосовать");
    case 12 -> mc.setScreen(new CommunityScreen(null,"ideas",""));
    case 13 -> clickCard();
    case 14 -> mc.setScreen(new CommunityScreen(null,"board",""));
    case 15 -> clickCard();
    case 16 -> {mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new CommunityScreen(null,"groups",""));}
    case 17 -> clickCard();
    case 18 -> {mc.options.guiScale().set(1);mc.resizeDisplay();FeatureListScreen.open(null,"players");}
    case 19 -> {for(var child:mc.screen.children())if(child instanceof PlayerRow row){row.onPress();break;}}
    case 20 -> mc.setScreen(new PlayerActionsScreen(mc.screen,player(),actions(),true));
    case 21 -> mc.setScreen(new ChoicePopup(mc.screen,"Проверка модального слоя",List.of("Первый вариант","Второй вариант"),i->{}));
    case 22 -> mc.setScreen(new NotificationPopup(new CommunityScreen(null,"home","")));
    case 23 -> {mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new PlayerActionsScreen(null,player(),actions()));}
    case 24 -> mc.setScreen(new AccessibilityScreen(mc.screen));
    case 25 -> {clickPrefix("Контрастность:");}
    case 26 -> mc.setScreen(new VoiceDiagnosticsScreen(null));
    case 27 -> mc.setScreen(new ServerMenuScreen(null,"admin"));
    case 28 -> clickAdminTab("Сервер");
    case 29 -> mc.setScreen(new ModerationVoteScreen(null,player()));
    case 30 -> mc.setScreen(new ModerationVoteScreen(null,null));
    case 31 -> mc.setScreen(new PlayerAdministrationScreen(null,player()));
    case 32 -> {mc.options.guiScale().set(1);mc.resizeDisplay();mc.setScreen(new CommunityScreen(null,"home",""));}
    case 33 -> mc.setScreen(new PlayerActionsScreen(mc.screen,player(),actions(),true));
    case 34 -> mc.setScreen(new ChoicePopup(mc.screen,"Действия игрока",List.of("Открыть уведомления","Другой вариант"),i->{}));
    case 35 -> {mc.screen.keyPressed(258,0,0);if(mc.screen.getFocused()==null)throw new IllegalStateException("Tab did not focus popup");mc.setScreen(new NotificationPopup(mc.screen));}
    case 36 -> {mc.options.guiScale().set(2);mc.resizeDisplay();}
    case 37 -> {mc.screen.keyPressed(256,0,0);if(!(mc.screen instanceof ChoicePopup))throw new IllegalStateException("Esc did not restore popup");}
    case 38 -> {mc.screen.keyPressed(256,0,0);if(!(mc.screen instanceof PlayerActionsScreen))throw new IllegalStateException("Esc did not restore moderation");mc.screen.mouseScrolled(mc.screen.width/2,mc.screen.height/2,0,-5);}
    case 39 -> {mc.options.guiScale().set(1);mc.resizeDisplay();mc.setScreen(new CommunityScreen(null,"home",""));}
    case 40 -> clickPrefix("Объявление администрации");
    case 41 -> {if(!(mc.screen instanceof TextScreen))throw new IllegalStateException("Pinned announcement did not open");mc.setScreen(new CommunityScreen(null,"polls",""));}
    case 42 -> clickPrefix("Нарушение правил");
    case 43 -> {if(!(mc.screen instanceof ModerationVoteScreen))throw new IllegalStateException("Community vote card did not open");}
    case 44 -> {var diag=new JsonObject();diag.addProperty("kind","diagnostics");diag.addProperty("version","2.1.0");diag.addProperty("database","Доступна");diag.addProperty("databaseMillis",7);diag.addProperty("luckPerms",false);diag.addProperty("plasmoVoice",false);diag.add("players",new JsonArray());diag.add("recentErrors",new JsonArray());ServerMenuClient.receive(diag);if(!(mc.screen instanceof TextScreen))throw new IllegalStateException("Diagnostics did not open");}
    case 45 -> {mc.options.guiScale().set(1);mc.resizeDisplay();FeatureListScreen.open(null,"players");}
    case 46 -> {for(var child:mc.screen.children())if(child instanceof PlayerRow row){row.onPress();break;}}
    case 47 -> clickLabel("Написать");
    case 48 -> {if(!(mc.screen instanceof TextScreen))throw new IllegalStateException("Chat without a connection was not blocked");mc.setScreen(new PlayerActionsScreen(null,player(),actions()));}
    case 49 -> clickLabel("Написать");
    case 50 -> {if(!(mc.screen instanceof TextScreen))throw new IllegalStateException("Player actions chat without a connection was not blocked");System.out.println("ANTHUB_UI_CHAT_GUARD_OK");}
    case 51 -> {mc.options.guiScale().set(1);mc.resizeDisplay();open("normal");mc.setScreen(new ServerMenuScreen(null,"admin"));}
    case 52 -> clickAdminTab("Сервер");
    case 53 -> {ServerMenuClient.state.addProperty("maintenance",true);ServerMenuClient.state.addProperty("maintenanceUntil",System.currentTimeMillis()+1800000);ServerMenuClient.state.addProperty("maintenanceReason","Обновление сборки");ServerMenuClient.state.addProperty("restartAt",System.currentTimeMillis()+300000);ServerMenuClient.state.addProperty("pinned",true);ServerMenuClient.state.addProperty("pinnedText","Встреча на спавне");}
    case 54 -> {clickLabel("Изменить");}
    case 55 -> {mc.screen.onClose();}
    case 56 -> {clickAdminTab("Журнал");}
    case 57 -> {if(mc.screen.children().stream().noneMatch(c->c instanceof Button b&&b.getMessage().getString().equals("Экспорт сообщества")))throw new IllegalStateException("Direct export missing");}
    case 58 -> {open("normal");ServerMenuClient.state.addProperty("admin",false);ServerMenuClient.state.addProperty("staff",true);ServerMenuClient.state.add("capabilities",Json.GSON.toJsonTree(Map.of("anthub.reports",true)));mc.setScreen(new ServerMenuScreen(null,"admin"));}
    case 59 -> {for(var child:mc.screen.children())if(child instanceof Button b&&b.getX()>20&&List.of("Сервер","Журнал").contains(b.getMessage().getString()))throw new IllegalStateException("Limited moderator sees unrelated administration tabs");System.out.println("ANTHUB_UI_SCOPED_PERMISSIONS_OK");}
    case 60 -> {open("normal");mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new PlayerActionsScreen(null,player(),actions(),true));}
    case 61 -> clickLabel(Client.tr("server.command.kill").getString());
    case 62 -> {if(!(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen))throw new IllegalStateException("Kill needs confirmation");mc.setScreen(new ReportScreen(new CommunityScreen(null,"home",""),"Игрок: ABROSxd\n"));}
    case 63 -> {for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.MultiLineEditBox e)e.setValue("Первая строка жалобы\nВторая строка с подробностями");}
    case 64 -> clickLabel("Отправить");
    case 65 -> {if(mc.screen instanceof ReportScreen)throw new IllegalStateException("Report did not send directly");mc.setScreen(new ModerationVoteScreen(mc.screen,player()));}
    case 66 -> clickPrefix("Мера:");
    case 67 -> clickLabel("Временный бан");
    case 68 -> {mc.setScreen(new CommunityScreen(null,"groups",""));}
    case 69 -> clickCard();
    case 70 -> clickLabel("⋯");
    case 71 -> clickLabel("Редактировать");
    case 72 -> clickPrefix("Место:");
    case 73 -> {if(!(mc.screen instanceof LocationEditor))throw new IllegalStateException("Existing location was not preserved");mc.screen.onClose();}
    case 74 -> {open("normal");mc.options.guiScale().set(1);mc.resizeDisplay();}
    case 75 -> {mc.setScreen(new NotificationPopup(mc.screen));}
    case 76 -> clickLabel("Настройки уведомлений");
    case 77 -> {if(!(mc.screen instanceof CommunityPreferences))throw new IllegalStateException("Notification settings missing");System.out.println("ANTHUB_UI_REVISED_FORMS_OK");}
    case 78 -> {mc.options.guiScale().set(1);mc.resizeDisplay();open("empty");}
    case 79 -> {var field=CommunityScreen.class.getDeclaredField("rows");field.setAccessible(true);if(((java.util.List<?>)field.get(mc.screen)).size()!=1)throw new IllegalStateException("Empty home repeats its message");open("manygroups");}
    case 80 -> {mc.screen.mouseScrolled(mc.screen.width-100,290,0,-5);}
    case 81 -> {var field=CommunityScreen.class.getDeclaredField("groupScroll");field.setAccessible(true);if(field.getInt(mc.screen)<=0)throw new IllegalStateException("Groups do not scroll");mc.options.guiScale().set(2);mc.resizeDisplay();}
    case 82 -> {for(var child:mc.screen.children())if(child instanceof CommunityCard c&&c.getY()+c.getHeight()>mc.screen.height-40)throw new IllegalStateException("Home card overlaps footer");mc.screen.mouseScrolled(mc.screen.width-50,120,0,-5);}
    case 83 -> {mc.options.guiScale().set(1);mc.resizeDisplay();open("normal");mc.setScreen(new CommunityPreferences(mc.screen,Json.GSON.toJsonTree(Map.of("favorites",List.of(),"muted",List.of())).getAsJsonObject()));ServerMenuClient.previewTransport=j->{if(Json.opt(j,"op","").equals("preferences"))java.util.concurrent.CompletableFuture.delayedExecutor(1500,java.util.concurrent.TimeUnit.MILLISECONDS).execute(()->mc.execute(()->request(j)));else request(j);};}
    case 84 -> clickLabel("Сохранить");
    case 85 -> {if(!(mc.screen instanceof CommunityPreferences))throw new IllegalStateException("Preferences closed before response");for(var child:mc.screen.children())if(child instanceof Button b&&b.active)throw new IllegalStateException("Preferences can change while saving");}
    case 86 -> {if(mc.screen instanceof CommunityPreferences)throw new IllegalStateException("Preferences did not finish saving");System.out.println("ANTHUB_UI_EDGE_CASES_OK");}
    case 87 -> {open("empty");ServerMenuClient.previewTransport=j->{var out=new JsonObject();out.addProperty("kind","community");out.addProperty("request",Json.opt(j,"request",""));out.addProperty("section","notifications");out.add("preferences",new JsonObject());out.add("entries",new JsonArray());java.util.concurrent.CompletableFuture.delayedExecutor(50,java.util.concurrent.TimeUnit.MILLISECONDS).execute(()->mc.execute(()->ServerMenuClient.receive(out)));};mc.setScreen(new NotificationPopup(mc.screen));}
    case 88 -> {boolean found=false;for(var child:mc.screen.children())if(child instanceof Button b&&b.getMessage().getString().equals("Настройки уведомлений")){if(!b.active)throw new IllegalStateException("New player preferences are disabled");found=true;b.onPress();break;}if(!found||!(mc.screen instanceof CommunityPreferences))throw new IllegalStateException("Cannot open new player preferences");System.out.println("ANTHUB_EMPTY_PREFERENCES_OK");}
    case 89 -> {open("normal");mc.setScreen(new AccessibilityScreen(mc.screen));var c=AccessibilityScreen.class.getDeclaredField("contrast");c.setAccessible(true);var o=AccessibilityScreen.class.getDeclaredField("opaque");o.setAccessible(true);boolean oldC=c.getBoolean(null),oldO=o.getBoolean(null);try{for(boolean high:new boolean[]{false,true}){c.setBoolean(null,high);o.setBoolean(null,false);if(AccessibilityScreen.background(0xC01C242C)!=0xC01C242C)throw new IllegalStateException("Contrast overrides transparency");o.setBoolean(null,true);if(AccessibilityScreen.background(0xC01C242C)!=0xFF1C242C)throw new IllegalStateException("Opaque panels remain transparent");}}finally{c.setBoolean(null,oldC);o.setBoolean(null,oldO);}System.out.println("ANTHUB_PANEL_OPACITY_OK");}
    default -> {System.out.println("ANTHUB_UI_SMOKE_COMPLETE");if(System.getenv("ANTHUB_UI_KEEP_OPEN")!=null){smokeStep=-1;mc.options.guiScale().set(1);mc.resizeDisplay();open("normal");}else mc.stop();}
   }
  }catch(Exception failure){failure.printStackTrace();mc.stop();}
 }
 private static void clickAdminTab(String label){for(var child:Minecraft.getInstance().screen.children())if(child instanceof Button b&&b.getX()>20&&b.getMessage().getString().equals(label)){b.onPress();return;}throw new IllegalStateException("Admin tab not found: "+label);}
 private static void clickPrefix(String label){for(var child:Minecraft.getInstance().screen.children())if(child instanceof Button button&&button.getMessage().getString().startsWith(label)){button.onPress();return;}throw new IllegalStateException("No button: "+label);}
 private static void clickCard(){for(var child:Minecraft.getInstance().screen.children())if(child instanceof CommunityCard card&&!card.getMessage().getString().startsWith("Нарушение правил")){card.onPress();return;}throw new IllegalStateException("No card");}
 private static void clickLabel(String label){for(var child:Minecraft.getInstance().screen.children())if(child instanceof Button button&&button.getMessage().getString().equals(label)){button.onPress();return;}throw new IllegalStateException("No button: "+label);}
 private static void clickLastLabel(String label){Button last=null;for(var child:Minecraft.getInstance().screen.children())if(child instanceof Button button&&button.getMessage().getString().equals(label))last=button;if(last==null)throw new IllegalStateException("No button: "+label);last.onPress();}

 @SubscribeEvent public static void title(ScreenEvent.Init.Post event){if(!(event.getScreen() instanceof TitleScreen))return;ServerMenuClient.previewTransport=null;int y=35;for(String mode:List.of("normal","empty","long","slow","error","readonly")){String chosen=mode;event.addListener(Button.builder(Component.literal("UI: "+mode),b->open(chosen)).bounds(8,y,95,20).build());y+=22;}}
 private static void open(String mode){scenario=mode;var state=new JsonObject();state.addProperty("uuid",ACTOR);state.addProperty("name","UI Tester");state.addProperty("admin",!mode.equals("readonly"));state.addProperty("unread",0);state.addProperty("sessionSeconds",60);state.addProperty("online",200);state.addProperty("maximum",300);state.add("communityConfig",CommunityStore.defaults());state.add("features",ConnectionCompatibility.features());var voting=Json.GSON.toJsonTree(ModerationVotes.Settings.defaults()).getAsJsonObject();voting.addProperty("enabled",true);voting.add("actions",Json.GSON.toJsonTree(List.of("kick","ban","mute")));state.add("moderationVotes",voting);state.add("actions",actions());ServerMenuClient.state=state;ServerMenuClient.previewTransport=CommunityUiHarness::request;Minecraft.getInstance().setScreen(new CommunityScreen(new TitleScreen(),"home",""));}
 private static JsonObject entry(String section,int index){var j=new JsonObject();j.addProperty("id",new UUID(0,index+10).toString());j.addProperty("read",index%2==0);j.addProperty("target","");j.addProperty("at",System.currentTimeMillis());j.addProperty("section",section);j.addProperty("title",scenario.equals("long")?"Очень длинное название команды и события ".repeat(8):"Пример "+section+" "+index);j.addProperty("description","Описание для проверки переносов строк. ".repeat(scenario.equals("long")?40:3));j.addProperty("preview",Json.str(j,"description"));j.addProperty("owner",ACTOR);j.addProperty("isOwner",true);j.addProperty("author","Tester");j.addProperty("status",section.equals("ideas")?"new":"open");j.addProperty("revision",1);j.addProperty("manage",!scenario.equals("readonly"));j.addProperty("startsAt",System.currentTimeMillis()+3600000);j.addProperty("endsAt",System.currentTimeMillis()+86400000);j.addProperty("capacity",10);j.addProperty("type","Команда");j.addProperty("recruiting",true);if(section.equals("groups"))j.add("location",new CommunityLocation("Поселение","minecraft:overworld",3009,75,1856,false).json());j.addProperty("category","");for(String key:List.of("responses","members","applications","invitations","participants","supporters")){j.add(key,new JsonObject());j.addProperty(key+"Count",0);}for(String key:List.of("isMember","hasApplication","hasResponded","isParticipant","supported","voted","multiple","changeVote"))j.addProperty(key,false);j.addProperty("liveResults",true);var options=new JsonArray();options.add("Первый вариант");options.add("Второй вариант");j.add("options",options);var counts=new JsonArray();counts.add(20);counts.add(15);j.add("counts",counts);j.add("myVote",new JsonArray());j.add("votes",new JsonObject());if(section.equals("groups")){j.getAsJsonObject("members").addProperty(ACTOR,"leader");j.addProperty("membersCount",1);var application=new JsonObject();application.addProperty("name","Example Player");application.addProperty("text","Хочу присоединиться к вашей команде.");j.getAsJsonObject("applications").add(new UUID(0,99).toString(),application);j.addProperty("applicationsCount",1);}j.add("actions",CommunityPolicy.actions(j,new CommunityStore.Actor(ACTOR,"Tester",!scenario.equals("readonly"),true),System.currentTimeMillis()));return j;}
 private static JsonArray actions(){return Json.GSON.toJsonTree(List.of("tell","kick","ban","pardon","kill","vmute","vunmute")).getAsJsonArray();}
 private static JsonObject player(){var p=new JsonObject();p.addProperty("uuid","00000000-0000-0000-0000-000000000099");p.addProperty("name","ABROSxd");p.addProperty("prefix","<#FF5555>[Root] ");p.addProperty("suffix"," <#1E90FF>@abrosdaniel");p.addProperty("online",true);var stats=new JsonObject();stats.addProperty("firstJoin",System.currentTimeMillis()-86400000L);stats.addProperty("lastActivity",System.currentTimeMillis());stats.addProperty("timeSince",System.currentTimeMillis()-86400000L);stats.addProperty("totalMillis",123456789L);stats.addProperty("sessionMillis",123456L);stats.addProperty("deaths",123);p.add("statistics",stats);return p;}
 private static void request(JsonObject input){if(!Json.opt(input,"action","").equals("community")){var out=new JsonObject();out.addProperty("kind",Json.opt(input,"action",""));out.addProperty("request",Json.opt(input,"request",""));switch(Json.opt(input,"action","")){
  case "report" -> {if(!Json.str(input,"message").contains("\nВторая строка"))throw new IllegalStateException("Multiline report lost text");out.addProperty("text","Отправлено");}
  case "players" -> {out.addProperty("page",0);out.addProperty("nextCursor","");out.add("players",Json.GSON.toJsonTree(List.of(player())));out.add("actions",actions());}
  case "moderationVote" -> {var vote=new JsonObject();vote.addProperty("id","fixture-vote");vote.addProperty("name","ExamplePlayer");vote.addProperty("action","ban");vote.addProperty("reason","Повторное нарушение правил сервера");vote.addProperty("status","open");vote.addProperty("endsAt",System.currentTimeMillis()+120000);vote.addProperty("yes",3);vote.addProperty("no",1);vote.addProperty("eligibleCount",8);vote.addProperty("canVote",true);out.add("vote",vote);}
  case "playerAdministration" -> {out.addProperty("online",true);var rights=new JsonObject();rights.addProperty("available",true);rights.add("capabilities",Json.GSON.toJsonTree(Map.of("anthub.admin",true,"anthub.events",true,"anthub.auth.reset",true)));out.add("permissions",rights);out.add("actions",actions());var history=new JsonObject();history.addProperty("nextCursor","");history.add("entries",new JsonArray());out.add("history",history);}
  default -> {return;}
 }java.util.concurrent.CompletableFuture.delayedExecutor(50,java.util.concurrent.TimeUnit.MILLISECONDS).execute(()->Minecraft.getInstance().execute(()->ServerMenuClient.receive(out)));return;}if(Json.opt(input,"op","").equals("vote")){if(!input.has("choices")||input.getAsJsonArray("choices").isEmpty())throw new IllegalStateException("Vote button did not send selected choices");System.out.println("ANTHUB_UI_VOTE_PAYLOAD_OK");}var mc=Minecraft.getInstance();Runnable deliver=()->{var out=new JsonObject();out.addProperty("kind","community");out.addProperty("request",Json.opt(input,"request",""));if(scenario.equals("error")){out.addProperty("error",true);out.addProperty("code","UNAVAILABLE");out.addProperty("text","Тестовая ошибка: данные не потеряны");ServerMenuClient.receive(out);return;}String section=Json.opt(input,"section","home");out.addProperty("section",section);out.add("config",CommunityStore.defaults());out.add("preferences",Json.GSON.toJsonTree(Map.of("favorites",List.of(),"muted",List.of())));if(section.equals("home")&&!scenario.equals("empty"))out.add("pinnedAnnouncement",Json.GSON.toJsonTree(Map.of("text","Добро пожаловать! В субботу встречаемся на спавне — объявление администрации.","until",System.currentTimeMillis()+86400000)));out.add("groups",Json.GSON.toJsonTree(List.of(Map.of("value",new UUID(0,11).toString(),"label","Поселение игроков"))));if(scenario.equals("manygroups")){var groups=new JsonArray();for(int n=0;n<20;n++)groups.add(Json.GSON.toJsonTree(Map.of("value",new UUID(0,n+11).toString(),"label","Объединение "+(n+1))));out.add("groups",groups);}out.addProperty("canCreate",!scenario.equals("readonly"));out.addProperty("nextCursor","");String id=Json.opt(input,"id","");if(!id.isEmpty()){var detail=entry(section,0);detail.addProperty("id",id);out.add("detail",detail);out.add("names",new JsonObject());}else{var rows=new JsonArray();if(!scenario.equals("empty"))for(int i=0;i<30;i++)rows.add(entry(section.equals("home")?List.of("board","groups","events","polls","ideas").get(i%5):section,i));if(section.equals("notifications"))for(var e:rows)e.getAsJsonObject().addProperty("section","board");out.add("entries",rows);}ServerMenuClient.receive(out);};if(scenario.equals("slow"))java.util.concurrent.CompletableFuture.delayedExecutor(17,java.util.concurrent.TimeUnit.SECONDS).execute(()->mc.execute(deliver));else java.util.concurrent.CompletableFuture.delayedExecutor(50,java.util.concurrent.TimeUnit.MILLISECONDS).execute(()->mc.execute(deliver));}
}
