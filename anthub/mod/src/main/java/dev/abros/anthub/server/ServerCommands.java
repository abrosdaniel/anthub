package dev.abros.anthub.server;

import dev.abros.anthub.core.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.*;
import java.util.*;
import java.util.concurrent.*;

final class ServerCommands {
 static final List<String> RIGHTS=List.of("anthub.stats.edit","anthub.stats.view","anthub.announce","anthub.maintenance","anthub.restart","anthub.reports","anthub.diagnostics");
 static boolean allowed(CommandSourceStack s,String right){return s.getEntity() instanceof ServerPlayer p?allowed(p,right):s.getEntity()==null&&s.hasPermission(4);}
 static boolean allowed(ServerPlayer p,String right){if(!p.connection.getConnection().isConnected()||!AuthServer.authenticated(p))return false;if(p.hasPermissions(2))return true;if(!ServerIntegration.luckPermsEnabled())return false;var caps=LuckPermsAdapter.profile(p.getUUID()).getAsJsonObject("capabilities");return caps.has("anthub.admin")&&caps.get("anthub.admin").getAsBoolean()||caps.has(right)&&caps.get(right).getAsBoolean();}
 static boolean staff(ServerPlayer p){return ServerFeatures.admin(p)||RIGHTS.stream().filter(k->!k.startsWith("anthub.stats.")).anyMatch(k->allowed(p,k));}
 static void reply(CommandSourceStack s,String text){s.sendSuccess(()->Component.literal(text),false);}
 static void error(CommandSourceStack s,Exception ex){s.sendFailure(Component.literal(ex instanceof IllegalArgumentException?ex.getMessage():"Не удалось выполнить действие. Проверьте журнал сервера."));if(!(ex instanceof IllegalArgumentException))com.mojang.logging.LogUtils.getLogger().warn("AntHub command failed ({})",ex.getClass().getSimpleName());}
 private static RequiredArgumentBuilder<CommandSourceStack,String> player(){return Commands.argument("player",StringArgumentType.word()).suggests((c,b)->suggest(b));}
 static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder){
  var future=new CompletableFuture<Suggestions>();try{ServerPlayerStatistics.task(()->{try{for(var p:ServerPlayerStatistics.store().find(builder.getRemaining(),false))builder.suggest(p.name(),Component.literal(p.id().toString()));future.complete(builder.build());}catch(Exception ex){future.complete(builder.build());}});}catch(Exception ex){future.complete(builder.build());}return future;
 }
 static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher){
  var root=Commands.literal("ah");
  root.then(Commands.literal("help").executes(c->help(c.getSource(),""))
   .then(Commands.argument("section",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(sections(c.getSource()),b)).executes(c->help(c.getSource(),StringArgumentType.getString(c,"section")))));
  var stats=Commands.literal("stats").requires(s->allowed(s,"anthub.stats.view")||allowed(s,"anthub.stats.edit"));
  stats.then(Commands.literal("show").then(player().executes(c->run(c.getSource(),StringArgumentType.getString(c,"player"),"","",0))));
  var time=Commands.literal("time").requires(s->allowed(s,"anthub.stats.edit"));
  for(String op:List.of("set","add","subtract"))time.then(Commands.literal(op).then(player().then(Commands.argument("duration",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("5h","30m","120h30m"),b)).executes(c->{try{return run(c.getSource(),StringArgumentType.getString(c,"player"),"totalMillis",op,CommandValues.duration(StringArgumentType.getString(c,"duration")));}catch(Exception ex){error(c.getSource(),ex);return 0;}}))));
  stats.then(time);
  stats.then(Commands.literal("since").requires(s->allowed(s,"anthub.stats.edit")).then(Commands.literal("set").then(player().then(Commands.argument("date",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of(java.time.LocalDate.now().toString()),b)).executes(c->{try{return run(c.getSource(),StringArgumentType.getString(c,"player"),"firstJoin","set",CommandValues.date(StringArgumentType.getString(c,"date")));}catch(Exception ex){error(c.getSource(),ex);return 0;}})))));
  stats.then(Commands.literal("deaths").requires(s->allowed(s,"anthub.stats.edit")).then(Commands.literal("set").then(player().then(Commands.argument("count",LongArgumentType.longArg(0)).executes(c->run(c.getSource(),StringArgumentType.getString(c,"player"),"deaths","set",LongArgumentType.getLong(c,"count")))))));
  root.then(stats);
  root.then(Commands.literal("player").requires(s->allowed(s,"anthub.stats.view")||allowed(s,"anthub.stats.edit")).then(Commands.literal("info").then(player().executes(c->run(c.getSource(),StringArgumentType.getString(c,"player"),"","",0)))));
  dispatcher.register(root);
 }
 private static boolean access(CommandSourceStack s,boolean edit){return allowed(s,"anthub.stats.edit")||!edit&&allowed(s,"anthub.stats.view");}
 private static int run(CommandSourceStack source,String query,String field,String op,long value){
  try{if(!access(source,!field.isEmpty()))throw new IllegalArgumentException("Недостаточно прав");
   var store=ServerPlayerStatistics.store();ServerPlayerStatistics.task(()->{try{
    var people=store.find(query,true);if(people.isEmpty())throw new IllegalArgumentException("Игрок с сохранённой статистикой не найден");if(people.size()!=1)throw new IllegalArgumentException("Имя неоднозначно. Укажите серверный UUID");var person=people.getFirst();
    source.getServer().execute(()->{try{if(!access(source,!field.isEmpty()))throw new IllegalArgumentException("Права изменились");boolean online=source.getServer().getPlayerList().getPlayer(person.id())!=null;
     var checkpoint=ServerPlayerStatistics.capture(person.id());ServerPlayerStatistics.task(()->{try{
      if(!source.getServer().submit(()->access(source,!field.isEmpty())).get(2,TimeUnit.SECONDS))throw new IllegalArgumentException("Права изменились");
      String text;if(field.isEmpty()){if(checkpoint!=null)store.checkpoint(checkpoint);var j=store.snapshot(person.id());text=person.name()+" · "+person.id()+"\n"+(online?"В сети":"Не в сети");if(j.has("firstJoin"))text+="\nИграет с: "+CommandValues.dateText(j.get("firstJoin").getAsLong());if(j.has("totalMillis"))text+="\nВремя игры: "+CommandValues.durationText(j.get("totalMillis").getAsLong());if(checkpoint!=null)text+="\nСессия: "+CommandValues.durationText(checkpoint.elapsedMillis());if(j.has("deaths"))text+="\nСмерти: "+j.get("deaths").getAsLong();}
      else text=person.name()+": "+store.correct(person.id(),field,op,value,source.getTextName(),checkpoint);String answer=text;source.getServer().execute(()->{if(access(source,!field.isEmpty()))reply(source,answer);});
     }catch(Exception ex){source.getServer().execute(()->error(source,ex));}});
    }catch(Exception ex){error(source,ex);}});
   }catch(Exception ex){source.getServer().execute(()->error(source,ex));}});return 1;
  }catch(Exception ex){error(source,ex);return 0;}
 }
 private static List<String> sections(CommandSourceStack s){var out=new ArrayList<String>();out.add("menu");if(access(s,false))out.add("stats");for(String key:List.of("announce","maintenance","restart","reports","diagnostics"))if(allowed(s,"anthub."+key))out.add(key);if(AuthServer.enabled()&&(s.getEntity()==null&&s.hasPermission(4)||s.getEntity() instanceof ServerPlayer p&&AuthServer.mayReset(p)))out.add("auth");return out;}
 private static int help(CommandSourceStack s,String section){var available=sections(s);if(!section.isEmpty()&&!available.contains(section)){s.sendFailure(Component.literal("Раздел недоступен. /ah help"));return 0;}
  var lines=new ArrayList<String>();for(String key:available){if(!section.isEmpty()&&!section.equals(key))continue;lines.add(switch(key){
   case "stats" -> "/ah stats show <игрок> · /ah player info <игрок>"+(allowed(s,"anthub.stats.edit")?"\n/ah stats time set|add|subtract <игрок> 5h30m\n/ah stats since set <игрок> 2026-09-01\n/ah stats deaths set <игрок> 0":"");
   case "announce" -> "/ah announce <текст>\n/ah announce pin <минуты> <текст> · /ah announce unpin";
   case "maintenance" -> "/ah maintenance on <минуты> <причина> (0 — бессрочно)\n/ah maintenance off";
   case "restart" -> "/ah restart <секунды> <причина> · /ah restart cancel\nСохранение и остановка; повторный запуск обеспечивает панель.";
   case "reports" -> "/ah reports — обращения";
   case "diagnostics" -> "/ah status · /ah project status";
   case "auth" -> "/ah auth reset <игрок>"+(s.getEntity()==null?"\n/ah auth reserve|block|unblock <игрок>":"");
   default -> "/ah — меню сервера · /ah help [раздел]";});}reply(s,String.join("\n\n",lines));return 1;
 }
}
