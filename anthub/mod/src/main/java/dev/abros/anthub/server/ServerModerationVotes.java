package dev.abros.anthub.server;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.abros.anthub.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
final class ServerModerationVotes {
 private static final Map<UUID,String> announced=new HashMap<>();private static String published="";
 private static ModerationVotes votes;private static final AtomicBoolean checking=new AtomicBoolean();private static long lastCheck;
 static void install(){
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartingEvent e)->{try{votes=new ModerationVotes(ServerDatabase.get(),ServerDatabase.settings().votes());votes.restart();checking.set(false);lastCheck=0;announced.clear();published="";}catch(Exception failure){throw new IllegalStateException("Cannot initialize AntHub moderation votes",failure);}});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->tick(e.getServer()));
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e)->votes=null);
 }
 static JsonObject configuration(MinecraftServer server){var j=new JsonObject();if(votes==null)return j;var s=votes.settings();j.addProperty("enabled",s.enabled());j.addProperty("minimumPlayers",s.minimumPlayers());j.addProperty("minimumPlayMinutes",s.minimumPlayMinutes());j.addProperty("durationSeconds",s.durationSeconds());j.addProperty("banMinutes",30);j.addProperty("muteMinutes",15);j.addProperty("maximumPunishmentMinutes",1440);var actions=new JsonArray();if(s.kick())actions.add("kick");if(s.ban())actions.add("ban");if(s.mute()&&voiceAvailable(server))actions.add("mute");j.add("actions",actions);return j;}
 private static boolean voiceAvailable(MinecraftServer server){return PlasmoVoiceAdapter.available();}
 private static boolean protectedPlayer(MinecraftServer server,UUID id,String name){
  if(server.getPlayerList().isOp(new GameProfile(id,name)))return true;
  if(ServerIntegration.luckPermsEnabled()){var profile=LuckPermsAdapter.profile(id);if(!profile.has("available"))return true;var caps=profile.getAsJsonObject("capabilities");return caps.get("anthub.admin").getAsBoolean()||caps.get("anthub.vote.protected").getAsBoolean();}return false;
 }
 private record Snapshot(Set<UUID> eligible,String name,boolean protectedTarget,boolean voice){}
 static void request(ServerPlayer player,JsonObject request,java.util.function.Consumer<JsonObject> success,java.util.function.Consumer<Exception> failure){
  var store=votes;var server=player.server;String op=Json.opt(request,"op","view");
  ServerFeatures.storage(()->{try{
   if(!server.submit(()->player.connection.getConnection().isConnected()&&AuthServer.authenticated(player)).get(2,TimeUnit.SECONDS))throw new IllegalArgumentException("Сеанс завершён");
   JsonObject result;
   switch(op){
    case "view" -> result=store.current(player.getUUID());
    case "start" -> {
     UUID.fromString(Json.str(request,"operationId"));if(!request.has("issuedAt"))throw new IllegalArgumentException("Отсутствует время запроса");
     UUID target=UUID.fromString(Json.str(request,"target"));
     var snapshot=server.submit(()->{var targetPlayer=server.getPlayerList().getPlayer(target);if(targetPlayer==null||!AuthServer.authenticated(targetPlayer))throw new IllegalArgumentException("Игрок не в сети");var eligible=new HashSet<UUID>();for(var p:server.getPlayerList().getPlayers())if(AuthServer.authenticated(p))eligible.add(p.getUUID());String name=targetPlayer.getGameProfile().getName();return new Snapshot(Set.copyOf(eligible),name,protectedPlayer(server,target,name),voiceAvailable(server));}).get(2,TimeUnit.SECONDS);
     long played=ServerPlayerStatistics.store().recordedMillis(player.getUUID());
     result=store.start(player.getUUID(),target,snapshot.name,Json.str(request,"measure"),Json.str(request,"reason"),snapshot.eligible,played,snapshot.protectedTarget,snapshot.voice,request,System.currentTimeMillis());
     ServerExtras.audit(player.getGameProfile().getName(),"moderation vote started",Json.opt(result,"id","")+" · "+target);
    }
    case "vote" -> result=store.vote(player.getUUID(),Json.str(request,"id"),request.get("yes").getAsBoolean(),System.currentTimeMillis());
    case "cancel" -> {
     if(!server.submit(()->player.connection.getConnection().isConnected()&&AuthServer.authenticated(player)&&ServerFeatures.admin(player)).get(2,TimeUnit.SECONDS))throw new IllegalArgumentException("Недостаточно прав");
     result=store.cancel(Json.str(request,"id"),player.getGameProfile().getName(),Json.str(request,"reason"),System.currentTimeMillis());ServerExtras.audit(player.getGameProfile().getName(),"moderation vote cancelled",Json.str(request,"id"));
    }
    default -> throw new IllegalArgumentException("Неизвестное действие");
   }
   var response=new JsonObject();response.addProperty("kind","moderationVote");response.addProperty("request",Json.opt(request,"request",""));response.add("vote",result);server.execute(()->{if(store==votes&&player.connection.getConnection().isConnected()&&AuthServer.authenticated(player))success.accept(response);});
  }catch(Exception ex){Exception cause=ex instanceof ExecutionException&&ex.getCause() instanceof Exception nested?nested:ex;server.execute(()->failure.accept(cause));}});
 }
 private static void tick(MinecraftServer server){long now=System.currentTimeMillis();if(votes==null||!votes.settings().enabled()||now-lastCheck<1000||!checking.compareAndSet(false,true))return;lastCheck=now;var store=votes;
  try{ServerFeatures.storage(()->{try{var current=store.current(null);server.execute(()->publish(server,store,current));var claimed=store.claim(now);if(claimed==null)return;server.execute(()->{if(votes!=store)return;String outcome;boolean success=false;try{outcome=apply(server,claimed);success=true;}catch(Exception failure){outcome="Не применено: "+(failure instanceof IllegalArgumentException?failure.getMessage():"ошибка интеграции");}String result=outcome;boolean applied=success;try{ServerFeatures.storage(()->{try{store.finish(Json.str(claimed,"id"),applied,result,System.currentTimeMillis());}catch(Exception error){com.mojang.logging.LogUtils.getLogger().error("AntHub: cannot persist vote outcome [{}]; inspect moderation result",Json.str(claimed,"id"));}});}catch(RejectedExecutionException full){com.mojang.logging.LogUtils.getLogger().error("AntHub: vote outcome queue is full [{}]",Json.str(claimed,"id"));}});}catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("AntHub: cannot finalize moderation vote ({})",failure.getClass().getSimpleName());}finally{checking.set(false);}});}catch(RejectedExecutionException full){checking.set(false);}
 }
 private static void publish(MinecraftServer server,ModerationVotes store,JsonObject current){
  if(votes!=store)return;boolean active=Json.opt(current,"status","").equals("open")&&current.has("endsAt")&&current.get("endsAt").getAsLong()>System.currentTimeMillis();String key=active?Json.str(current,"id"):"";String serialized=Json.GSON.toJson(current);var connected=new HashSet<UUID>();for(var player:server.getPlayerList().getPlayers()){connected.add(player.getUUID());if(!AuthServer.authenticated(player)||!ServerIntegration.supports(player,"moderation-votes"))continue;String previous=announced.get(player.getUUID());if(!Objects.equals(previous,key)||!published.equals(serialized)){var packet=new JsonObject();packet.addProperty("kind","moderationVoteStatus");packet.add("vote",current);ServerFeatures.send(player,packet);}if(active&&!key.equals(previous)){String text="Голосование о наказании: "+Json.str(current,"name")+". Раздел «Голосования».";var notice=new JsonObject();notice.addProperty("kind","notice");notice.addProperty("text",text);notice.addProperty("optional",false);ServerFeatures.send(player,notice);player.sendSystemMessage(Component.literal(text));}announced.put(player.getUUID(),key);}announced.keySet().retainAll(connected);published=serialized;
 }
 private static String apply(MinecraftServer server,JsonObject vote)throws Exception{
  UUID id=UUID.fromString(Json.str(vote,"target"));String name=Json.str(vote,"name"),measure=Json.str(vote,"action"),reason="Голосование игроков: "+Json.str(vote,"reason");var player=server.getPlayerList().getPlayer(id);
  if(protectedPlayer(server,id,name))throw new IllegalArgumentException("игрок защищён от голосований");
  switch(measure){
   case "kick" -> {if(player==null)return "Кик не потребовался: игрок уже вышел";player.connection.disconnect(Component.literal(reason));return "Игрок отключён; повторный вход разрешён";}
   case "ban" -> {
    var profile=new GameProfile(id,name);if(server.getPlayerList().getBans().isBanned(profile))throw new IllegalArgumentException("игрок уже забанен; существующее наказание сохранено");
    var until=new Date(System.currentTimeMillis()+vote.get("minutes").getAsLong()*60000);server.getPlayerList().getBans().add(new net.minecraft.server.players.UserBanListEntry(profile,new Date(),"AntHub community vote",until,reason));if(player!=null)player.connection.disconnect(Component.literal(reason));return "Временный бан применён";
   }
   case "mute" -> {if(!voiceAvailable(server))throw new IllegalArgumentException("Plasmo Voice недоступен");PlasmoVoiceAdapter.mute(id,vote.get("minutes").getAsInt(),reason);return "Голосовой mute применён";}
   default -> throw new IllegalArgumentException("неизвестная мера");
  }
 }
}
