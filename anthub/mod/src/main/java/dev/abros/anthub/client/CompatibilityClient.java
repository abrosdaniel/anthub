package dev.abros.anthub.client;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.abros.anthub.compat.AccessDeniedBindings;
import dev.abros.anthub.core.Json;
import dev.abros.anthub.network.Protocol;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.concurrent.*;

/** Connection-scoped request lifecycle, independent of opening the AntHub menu. */
public final class CompatibilityClient {
 private record Pending(CompletableFuture<JsonObject> future,long deadline){}
 private static final Map<String,Pending> pending=new HashMap<>();
 private static final Map<UUID,GameProfile> profiles=new HashMap<>();
 private static final Set<UUID> loading=new HashSet<>();
 private static final Map<UUID,Long> retryAfter=new HashMap<>();
 private static Object connection;
 private CompatibilityClient(){}
 public static void install(){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event)->{
  var screen=event.getScreen();if(!active()||!screen.getClass().getName().equals(AccessDeniedBindings.BASE+"screen.AccessControlScreen"))return;
  try{var field=screen.getClass().getDeclaredField("networkId");field.setAccessible(true);var network=(UUID)field.get(screen);int x=screen.width/2-96,y=screen.height/2-59;UiActions.row(new dev.abros.anthub.core.NativeLayout.Box(x+58,y+123,134,20),event::addListener,UiActions.action("Проверить доступ",()->preview(network,screen),true));}
  catch(ReflectiveOperationException error){com.mojang.logging.LogUtils.getLogger().warn("AntHub Access Denied screen contract changed",error);}
 });}
 private static void preview(UUID network,net.minecraft.client.gui.screens.Screen parent){
  var j=new JsonObject();j.addProperty("op","preview");j.addProperty("network",network.toString());request(AccessDeniedBindings.ID,j).whenComplete((reply,error)->{
   var mc=Minecraft.getInstance();if(mc.screen!=parent)return;if(error!=null){mc.setScreen(new TextScreen(parent,Component.literal("Проверка доступа"),error.getMessage(),true));return;}
   var text=new StringBuilder(Json.str(reply,"text"));for(var item:reply.getAsJsonArray("replacements")){var row=item.getAsJsonObject();text.append("\n").append(Json.str(row,"name")).append(": ").append(Json.str(row,"from")).append(" → ").append(Json.str(row,"to"));}
   text.append("\n\nНеподтверждённые записи не изменяются. Если игрок всё ещё не имеет доступа, удалите старую запись и добавьте его по серверному нику.");
   if(!reply.has("token")){mc.setScreen(new TextScreen(parent,Component.literal("Проверка доступа"),text.toString(),true));return;}
   mc.setScreen(new UiConfirmDialog(yes->{mc.setScreen(parent);if(yes){var apply=new JsonObject();apply.addProperty("op","apply");apply.addProperty("network",network.toString());apply.addProperty("token",Json.str(reply,"token"));request(AccessDeniedBindings.ID,apply).whenComplete((result,failure)->message(failure==null?Json.str(result,"text"):failure.getMessage()));}},Component.literal("Исправить доступ?"),Component.literal(text.toString())));
  });
 }
 public static boolean active(){return Minecraft.getInstance().getConnection()!=null&&Protocol.supportedFeatures.contains("compatibility-adapters")&&AccessDeniedBindings.supported();}
 public static void tick(){
  var current=Minecraft.getInstance().getConnection();if(current!=connection){connection=current;var previous=List.copyOf(pending.values());pending.clear();profiles.clear();loading.clear();retryAfter.clear();previous.forEach(p->p.future().completeExceptionally(new IllegalStateException("Соединение изменилось")));}
  long now=System.currentTimeMillis();var expired=new ArrayList<String>();pending.forEach((id,p)->{if(p.deadline()<now)expired.add(id);});for(var id:expired){var p=pending.remove(id);p.future().completeExceptionally(new IllegalArgumentException("Сервер не ответил. Повторите действие"));}
 }
 public static CompletableFuture<JsonObject> request(String adapter,JsonObject j){
  var mc=Minecraft.getInstance();if(!mc.isSameThread()){var future=new CompletableFuture<JsonObject>();var copy=j.deepCopy();mc.execute(()->request(adapter,copy).whenComplete((reply,error)->{if(error!=null)future.completeExceptionally(error);else future.complete(reply);}));return future;}
  tick();var result=new CompletableFuture<JsonObject>();if(Minecraft.getInstance().getConnection()==null||!Protocol.supportedFeatures.contains("compatibility-adapters")||pending.size()>=24){result.completeExceptionally(new IllegalArgumentException("Адаптер сейчас недоступен"));return result;}
  String id=UUID.randomUUID().toString();j.addProperty("action","compatibility");j.addProperty("adapter",adapter);j.addProperty("request",id);pending.put(id,new Pending(result,System.currentTimeMillis()+10000));PacketDistributor.sendToServer(new Protocol.FeatureRequest(Json.GSON.toJson(j)));return result;
 }
 static boolean receive(JsonObject j){if(!Json.opt(j,"kind","").equals("compatibility"))return false;if(!Minecraft.getInstance().isSameThread()){var copy=j.deepCopy();Minecraft.getInstance().execute(()->receive(copy));return true;}var p=pending.remove(Json.opt(j,"request",""));if(p!=null){if(j.has("error"))p.future().completeExceptionally(new IllegalArgumentException(Json.str(j,"error")));else p.future().complete(j);}return true;}
 public static void add(UUID network,String name){if(!dev.abros.anthub.core.auth.AuthStore.validName(name))return;var j=new JsonObject();j.addProperty("op","add");j.addProperty("network",network.toString());j.addProperty("name",name);request(AccessDeniedBindings.ID,j).whenComplete((reply,error)->{if(error!=null)message(error.getMessage());else{remember(reply);message(Json.str(reply,"text"));}});}
 private static void message(String text){var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(Component.literal(text),false);}
 private static void remember(JsonObject reply){if(profiles.size()>=512)profiles.clear();var id=UUID.fromString(Json.str(reply,"uuid"));profiles.put(id,new GameProfile(id,Json.str(reply,"name")));}
 /** Rendering must never block on an HTTP lookup. Unknown UUIDs retry with backoff. */
 public static GameProfile profile(UUID id){
  var current=Minecraft.getInstance();if(!current.isSameThread()){current.execute(()->profile(id));return new GameProfile(id,id.toString());}
  tick();
  var p=profiles.get(id);if(p!=null)return p;var mc=Minecraft.getInstance();var online=mc.getConnection().getPlayerInfo(id);if(online!=null)return online.getProfile();
  long now=System.currentTimeMillis();if(loading.size()<12&&now>=retryAfter.getOrDefault(id,0L)&&loading.add(id)){var j=new JsonObject();j.addProperty("op","lookup");j.addProperty("uuid",id.toString());request(AccessDeniedBindings.ID,j).whenComplete((reply,error)->{loading.remove(id);if(error==null)remember(reply);else{if(retryAfter.size()>=512)retryAfter.clear();retryAfter.put(id,System.currentTimeMillis()+30000);}});}
  return new GameProfile(id,id.toString());
 }
}
