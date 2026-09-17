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
  String output=System.getenv("ANTHUB_UI_SMOKE");if(output==null)return;var mc=Minecraft.getInstance();
  if(!smokeStarted){if(!(event.getScreen() instanceof TitleScreen))return;smokeStarted=true;smokeAt=System.currentTimeMillis()+1500;return;}
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
    default -> {System.out.println("ANTHUB_UI_SMOKE_COMPLETE");mc.stop();}
   }
  }catch(Exception failure){failure.printStackTrace();mc.stop();}
 }
 private static void clickCard(){for(var child:Minecraft.getInstance().screen.children())if(child instanceof CommunityCard card){card.onPress();return;}throw new IllegalStateException("No card");}
 private static void clickLabel(String label){for(var child:Minecraft.getInstance().screen.children())if(child instanceof Button button&&button.getMessage().getString().equals(label)){button.onPress();return;}throw new IllegalStateException("No button: "+label);}
 private static void clickLastLabel(String label){Button last=null;for(var child:Minecraft.getInstance().screen.children())if(child instanceof Button button&&button.getMessage().getString().equals(label))last=button;if(last==null)throw new IllegalStateException("No button: "+label);last.onPress();}

 @SubscribeEvent public static void title(ScreenEvent.Init.Post event){if(!(event.getScreen() instanceof TitleScreen))return;ServerMenuClient.previewTransport=null;int y=35;for(String mode:List.of("normal","empty","long","slow","error","readonly")){String chosen=mode;event.addListener(Button.builder(Component.literal("UI: "+mode),b->open(chosen)).bounds(8,y,95,20).build());y+=22;}}
 private static void open(String mode){scenario=mode;var state=new JsonObject();state.addProperty("uuid",ACTOR);state.addProperty("name","UI Tester");state.addProperty("admin",!mode.equals("readonly"));state.addProperty("unread",0);state.addProperty("sessionSeconds",60);state.addProperty("online",200);state.addProperty("maximum",300);state.add("communityConfig",CommunityStore.defaults());ServerMenuClient.state=state;ServerMenuClient.previewTransport=CommunityUiHarness::request;Minecraft.getInstance().setScreen(new CommunityScreen(new TitleScreen(),"home",""));}
 private static JsonObject entry(String section,int index){var j=new JsonObject();j.addProperty("id",new UUID(0,index+10).toString());j.addProperty("read",index%2==0);j.addProperty("target","");j.addProperty("at",System.currentTimeMillis());j.addProperty("section",section);j.addProperty("title",scenario.equals("long")?"Очень длинное название команды и события ".repeat(8):"Пример "+section+" "+index);j.addProperty("description","Описание для проверки переносов строк. ".repeat(scenario.equals("long")?40:3));j.addProperty("preview",Json.str(j,"description"));j.addProperty("owner",ACTOR);j.addProperty("author","Tester");j.addProperty("status",section.equals("ideas")?"new":"open");j.addProperty("revision",1);j.addProperty("manage",!scenario.equals("readonly"));j.addProperty("startsAt",System.currentTimeMillis()+3600000);j.addProperty("endsAt",System.currentTimeMillis()+86400000);j.addProperty("capacity",10);j.addProperty("type","Команда");j.addProperty("recruiting",true);j.addProperty("category","");for(String key:List.of("responses","members","applications","invitations","participants","supporters")){j.add(key,new JsonObject());j.addProperty(key+"Count",0);}for(String key:List.of("isMember","hasApplication","hasResponded","isParticipant","supported","voted","multiple","changeVote"))j.addProperty(key,false);j.addProperty("liveResults",true);var options=new JsonArray();options.add("Первый вариант");options.add("Второй вариант");j.add("options",options);var counts=new JsonArray();counts.add(20);counts.add(15);j.add("counts",counts);j.add("myVote",new JsonArray());j.add("votes",new JsonObject());if(section.equals("groups")){j.getAsJsonObject("members").addProperty(ACTOR,"leader");j.addProperty("membersCount",1);var application=new JsonObject();application.addProperty("name","Example Player");application.addProperty("text","Хочу присоединиться к вашей команде.");j.getAsJsonObject("applications").add(new UUID(0,99).toString(),application);j.addProperty("applicationsCount",1);}j.add("actions",CommunityPolicy.actions(j,new CommunityStore.Actor(ACTOR,"Tester",!scenario.equals("readonly"),true),System.currentTimeMillis()));return j;}
 private static void request(JsonObject input){if(!Json.opt(input,"action","").equals("community"))return;if(Json.opt(input,"op","").equals("vote")){if(!input.has("choices")||input.getAsJsonArray("choices").isEmpty())throw new IllegalStateException("Vote button did not send selected choices");System.out.println("ANTHUB_UI_VOTE_PAYLOAD_OK");}var mc=Minecraft.getInstance();Runnable deliver=()->{var out=new JsonObject();out.addProperty("kind","community");out.addProperty("request",Json.opt(input,"request",""));if(scenario.equals("error")){out.addProperty("error",true);out.addProperty("code","UNAVAILABLE");out.addProperty("text","Тестовая ошибка: данные не потеряны");ServerMenuClient.receive(out);return;}String section=Json.opt(input,"section","home");out.addProperty("section",section);out.add("config",CommunityStore.defaults());out.add("preferences",new JsonObject());out.add("groups",new JsonArray());out.addProperty("canCreate",!scenario.equals("readonly"));out.addProperty("nextCursor","");String id=Json.opt(input,"id","");if(!id.isEmpty()){var detail=entry(section,0);detail.addProperty("id",id);out.add("detail",detail);out.add("names",new JsonObject());}else{var rows=new JsonArray();if(!scenario.equals("empty"))for(int i=0;i<30;i++)rows.add(entry(section.equals("home")?List.of("board","groups","events","polls","ideas").get(i%5):section,i));if(section.equals("notifications"))for(var e:rows)e.getAsJsonObject().addProperty("section","board");out.add("entries",rows);}ServerMenuClient.receive(out);};if(scenario.equals("slow"))java.util.concurrent.CompletableFuture.delayedExecutor(17,java.util.concurrent.TimeUnit.SECONDS).execute(()->mc.execute(deliver));else java.util.concurrent.CompletableFuture.delayedExecutor(50,java.util.concurrent.TimeUnit.MILLISECONDS).execute(()->mc.execute(deliver));}
}
