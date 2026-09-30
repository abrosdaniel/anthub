package dev.abros.anthub.server;

import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Server-owned menu data and permission-preserving moderation. */
final class ServerExtras {
    private static dev.abros.anthub.core.CommunityStore database;
    private static JsonObject menu=new JsonObject();
    static void start(Path root,dev.abros.anthub.core.CommunityStore db)throws Exception{database=db;reload();}
    static void reload()throws Exception{
        menu=ServerDatabase.settings().menu();
    }
    static void menu(JsonObject value){menu=value.deepCopy();}
    static JsonObject menu(){return menu.deepCopy();}
    static JsonArray actions(ServerPlayer p){var result=new JsonArray();for(String action:List.of("tell","kick","warn","ban","pardon","kill","vmute","vunmute")){
        var node=p.server.getCommands().getDispatcher().getRoot().getChild(action.equals("warn")?"kick":action);if(node!=null&&node.canUse(p.createCommandSourceStack()))result.add(action);
    }return result;}
    static void moderate(ServerPlayer p,JsonObject j)throws Exception{
        String action=Json.str(j,"operation");if(!List.of("kick","warn","ban","pardon","kill","vmute","vunmute").contains(action))throw new IllegalArgumentException("Unknown moderation action");
        String targetId=UUID.fromString(Json.str(j,"target")).toString();var target=p.server.getPlayerList().getPlayer(UUID.fromString(targetId));
        if(target==null&&!List.of("ban","pardon").contains(action))throw new IllegalArgumentException("Player is offline");
        String onlineName=target==null?null:target.getGameProfile().getName();String reason=Json.opt(j,"reason","").strip();
        if(reason.length()>300||reason.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid reason");
        String suffix="";
        if(action.equals("kick")||action.equals("ban")||action.equals("warn")){if(reason.isEmpty())throw new IllegalArgumentException("Reason is required");suffix=" "+reason;}
        if(action.equals("vmute")){int minutes=j.get("minutes").getAsInt();if(minutes<1||minutes>10080)throw new IllegalArgumentException("Mute duration must be 1–10080 minutes");suffix=" "+minutes+"m"+(reason.isEmpty()?"":" "+reason);}
        int banMinutes=action.equals("ban")&&j.has("minutes")?j.get("minutes").getAsBigDecimal().intValueExact():0;if(banMinutes<0||banMinutes>10080)throw new IllegalArgumentException("Срок бана: 0 (бессрочно) или 1–10080 минут");
        String arguments=suffix,actor=p.getGameProfile().getName();var connection=p.connection;var server=p.server;
        var node=server.getCommands().getDispatcher().getRoot().getChild(action.equals("warn")?"kick":action);if(node==null||!node.canUse(p.createCommandSourceStack()))throw new IllegalArgumentException("Command unavailable or permission denied");
        ServerFeatures.storage(()->{try{
            var entry=new JsonObject();entry.addProperty("uuid",targetId);entry.addProperty("actor",actor);entry.addProperty("action",action);entry.addProperty("reason",reason);entry.addProperty("at",System.currentTimeMillis());entry.addProperty("outcome","Принято, результат ещё не подтверждён");if(action.equals("ban")&&banMinutes>0)entry.addProperty("durationMinutes",banMinutes);if(action.equals("vmute"))entry.addProperty("durationMinutes",j.get("minutes").getAsInt());
            String operationId=Json.str(j,"operationId");
            var receipt=database.receipt(p.getUUID().toString(),j,()->{database.record("moderation-history",operationId,entry);var claimed=new JsonObject();claimed.addProperty("accepted",true);return claimed;});
            if(receipt.has("replayed")){server.execute(()->p.sendSystemMessage(Component.literal("Этот запрос уже принят. Проверьте результат и журнал; повторно он не выполнен.")));return;}
            String name=onlineName==null?database.personName(targetId):onlineName;if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Unsupported player name");
            String command=action+" "+name+arguments;audit(actor,"moderation requested",command);
            server.execute(()->{if(p.connection!=connection||!connection.getConnection().isConnected()||!AuthServer.authenticated(p)){finishModeration(operationId,entry,"Отменено: сеанс модератора завершён");return;}
                var current=server.getCommands().getDispatcher().getRoot().getChild(action.equals("warn")?"kick":action);var source=p.createCommandSourceStack();
                if(current==null||!current.canUse(source)){finishModeration(operationId,entry,"Отказ: права изменились");p.sendSystemMessage(Component.literal("Permission denied"));return;}
                try{if(action.equals("warn")){var recipient=server.getPlayerList().getPlayer(UUID.fromString(targetId));if(recipient==null)throw new IllegalArgumentException("Player left");recipient.sendSystemMessage(Component.literal("Предупреждение администрации: "+reason));finishModeration(operationId,entry,"Применено");return;}if(action.equals("ban")&&banMinutes>0){var profile=new com.mojang.authlib.GameProfile(UUID.fromString(targetId),name);if(server.getPlayerList().getBans().isBanned(profile))throw new IllegalArgumentException("Player already banned");server.getPlayerList().getBans().add(new net.minecraft.server.players.UserBanListEntry(profile,new java.util.Date(),actor,new java.util.Date(System.currentTimeMillis()+banMinutes*60000L),reason));var recipient=server.getPlayerList().getPlayer(UUID.fromString(targetId));if(recipient!=null)recipient.connection.disconnect(Component.literal(reason));finishModeration(operationId,entry,"Применено");return;}int result=server.getCommands().getDispatcher().execute(command,source);finishModeration(operationId,entry,result>0?"Применено":"Команда не подтвердила применение");}catch(Exception ex){finishModeration(operationId,entry,"Ошибка применения");p.sendSystemMessage(Component.literal("Moderation command failed; see server feedback"));}
            });
        }catch(Exception ex){server.execute(()->p.sendSystemMessage(Component.literal("Cannot prepare moderation action; check target and server storage")));}});
    }
    private static void finishModeration(String id,JsonObject original,String outcome){
        var entry=original.deepCopy();entry.addProperty("outcome",outcome);entry.addProperty("finishedAt",System.currentTimeMillis());
        if(outcome.equals("Применено")&&entry.has("durationMinutes"))entry.addProperty("until",System.currentTimeMillis()+entry.get("durationMinutes").getAsLong()*60000);
        try{ServerFeatures.storage(()->{try{database.record("moderation-history",id,entry);audit(Json.str(entry,"actor"),"moderation result",Json.str(entry,"action")+" "+Json.str(entry,"uuid")+" · "+outcome);}catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("AntHub: cannot persist moderation result [{}]",id);}});}catch(java.util.concurrent.RejectedExecutionException busy){com.mojang.logging.LogUtils.getLogger().error("AntHub: moderation result queue is full [{}]",id);}
    }
    static void audit(String actor,String action,String detail)throws Exception{database.audit(actor,action,detail);}
    static JsonArray history(int page)throws Exception{if(page<0||page>100)throw new IllegalArgumentException("Invalid page");var out=new JsonArray();database.recordPage("audit","",page,10).forEach(out::add);return out;}
}
