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
    private static Path directory;private static dev.abros.anthub.core.CommunityStore database;
    private static JsonObject menu=new JsonObject();
    static void start(Path root,dev.abros.anthub.core.CommunityStore db)throws Exception{directory=root;database=db;reload();}
    static void reload()throws Exception{
        Path path=directory.resolve("server-menu.json");
        if(Files.isSymbolicLink(path))throw new IllegalArgumentException("Unsafe menu path");
        if(!Files.exists(path)){var empty=new JsonObject();empty.add("links",new JsonArray());Json.write(path,empty);}
        if(Files.size(path)>16000)throw new IllegalArgumentException("Menu data too large");
        menu=dev.abros.anthub.core.ServerMenuData.validate(Json.read(path));
    }
    static JsonObject menu(){return menu.deepCopy();}
    static JsonArray actions(ServerPlayer p){var result=new JsonArray();for(String action:List.of("tell","kick","ban","pardon","kill","vmute","vunmute")){
        var node=p.server.getCommands().getDispatcher().getRoot().getChild(action);if(node!=null&&node.canUse(p.createCommandSourceStack()))result.add(action);
    }return result;}
    static void moderate(ServerPlayer p,JsonObject j)throws Exception{
        String action=Json.str(j,"operation");if(!List.of("kick","ban","pardon","kill","vmute","vunmute").contains(action))throw new IllegalArgumentException("Unknown moderation action");
        String targetId=UUID.fromString(Json.str(j,"target")).toString();var target=p.server.getPlayerList().getPlayer(UUID.fromString(targetId));
        if(target==null&&!List.of("ban","pardon").contains(action))throw new IllegalArgumentException("Player is offline");
        String onlineName=target==null?null:target.getGameProfile().getName();String reason=Json.opt(j,"reason","").strip();
        if(reason.length()>300||reason.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid reason");
        String suffix="";
        if(action.equals("kick")||action.equals("ban")){if(reason.isEmpty())throw new IllegalArgumentException("Reason is required");suffix=" "+reason;}
        if(action.equals("vmute")){int minutes=j.get("minutes").getAsInt();if(minutes<1||minutes>10080)throw new IllegalArgumentException("Mute duration must be 1–10080 minutes");suffix=" "+minutes+"m"+(reason.isEmpty()?"":" "+reason);}
        String arguments=suffix,actor=p.getGameProfile().getName();var connection=p.connection;var server=p.server;
        var node=server.getCommands().getDispatcher().getRoot().getChild(action);if(node==null||!node.canUse(p.createCommandSourceStack()))throw new IllegalArgumentException("Command unavailable or permission denied");
        ServerFeatures.storage(()->{try{
            String name=onlineName==null?database.personName(targetId):onlineName;if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Unsupported player name");
            String command=action+" "+name+arguments;audit(actor,"moderation requested",command);
            server.execute(()->{if(p.connection!=connection||!connection.getConnection().isConnected()||!AuthServer.authenticated(p))return;
                var current=server.getCommands().getDispatcher().getRoot().getChild(action);var source=p.createCommandSourceStack();
                if(current==null||!current.canUse(source)){p.sendSystemMessage(Component.literal("Permission denied"));return;}
                try{server.getCommands().getDispatcher().execute(command,source);ServerFeatures.storage(()->{try{audit(actor,"moderation dispatched",command);}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().error("Cannot persist moderation audit",ex);}});}catch(Exception ex){p.sendSystemMessage(Component.literal("Moderation command failed; see server feedback"));}
            });
        }catch(Exception ex){server.execute(()->p.sendSystemMessage(Component.literal("Cannot prepare moderation action; check target and server storage")));}});
    }
    static void audit(String actor,String action,String detail)throws Exception{database.audit(actor,action,detail);}
    static JsonArray history(int page)throws Exception{if(page<0||page>100)throw new IllegalArgumentException("Invalid page");var out=new JsonArray();database.recordPage("audit","",page,10).forEach(out::add);return out;}
}
