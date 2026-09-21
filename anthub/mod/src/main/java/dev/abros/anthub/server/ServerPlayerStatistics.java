package dev.abros.anthub.server;

import dev.abros.anthub.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Game-thread observations, serialized storage and monotonic playtime. */
final class ServerPlayerStatistics {
    private static final class Session{
        final UUID id=UUID.randomUUID();final long started=System.currentTimeMillis(),nano=System.nanoTime();long deaths;int lastDeathTick=Integer.MIN_VALUE;ServerPlayer lastDeathEntity;
    }
    private static final Map<UUID,Session> sessions=new HashMap<>();
    private static ExecutorService writer;
    private static PlayerStatistics store;
    private static long lastCheckpoint;
    static void install(){
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartingEvent e)->start());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e)->{if(e.getEntity() instanceof ServerPlayer p)end(p.getUUID());});
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e)->{if(e.getEntity() instanceof ServerPlayer p&&AuthServer.authenticated(p)){Session s=sessions.get(p.getUUID());if(s!=null&&store.settings().deaths()&&(s.lastDeathEntity!=p||s.lastDeathTick!=p.tickCount)){s.lastDeathEntity=p;s.lastDeathTick=p.tickCount;s.deaths++;}}});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e)->stop());
    }
    private static void start(){
        sessions.clear();lastCheckpoint=0;
        try{
            store=new PlayerStatistics(ServerDatabase.get(),ServerDatabase.settings().statistics());
            writer=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1024),r->{var t=new Thread(r,"AntHub player statistics");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        }catch(Exception failure){throw new IllegalStateException("Cannot initialize statistics in config/anthub-server.toml",failure);}
    }
    private static void tick(MinecraftServer server){
        if(store==null)return;
        for(var player:server.getPlayerList().getPlayers())if(AuthServer.authenticated(player)&&!sessions.containsKey(player.getUUID())){
            var session=new Session();sessions.put(player.getUUID(),session);save(player.getUUID(),session,true);
        }
        long now=System.nanoTime();if(now-lastCheckpoint<TimeUnit.SECONDS.toNanos(30))return;lastCheckpoint=now;
        for(var entry:sessions.entrySet())save(entry.getKey(),entry.getValue(),true);
    }
    private static long elapsed(Session s){return Math.max(0,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-s.nano));}
    static Map<UUID,Long> live(){Map<UUID,Long> result=new HashMap<>();sessions.forEach((id,s)->result.put(id,elapsed(s)));return Map.copyOf(result);}
    static PlayerStatistics store(){return store;}
    static void task(Runnable task){if(writer==null||store==null)throw new IllegalArgumentException("Статистика недоступна");writer.execute(task);}
    static PlayerStatistics.Checkpoint capture(UUID player){var s=sessions.get(player);return s==null?null:new PlayerStatistics.Checkpoint(player,s.id,s.started,Math.max(s.started,System.currentTimeMillis()),elapsed(s),s.deaths,true);}

    private static void save(UUID player,Session session,boolean online){
        var checkpoint=new PlayerStatistics.Checkpoint(player,session.id,session.started,Math.max(session.started,System.currentTimeMillis()),elapsed(session),session.deaths,online);
        var current=store;
        try{writer.execute(()->{
            for(int attempt=0;attempt<3;attempt++)try{current.checkpoint(checkpoint);return;}catch(Exception failure){
                boolean retry=failure instanceof java.sql.SQLException||failure instanceof CommunityFailure;
                if(!retry||attempt==2){com.mojang.logging.LogUtils.getLogger().warn("AntHub: statistics checkpoint not saved ({}, online={}); previously saved totals retained",failure.getClass().getSimpleName(),online);return;}
                try{Thread.sleep(200L*(attempt+1));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}
            }
        });}catch(RejectedExecutionException busy){com.mojang.logging.LogUtils.getLogger().warn("AntHub: player statistics queue is full");}
    }
    private static void end(UUID player){var session=sessions.remove(player);if(session!=null)save(player,session,false);}
    private static void stop(){
        for(UUID player:List.copyOf(sessions.keySet()))end(player);
        if(writer!=null){writer.shutdown();try{if(!writer.awaitTermination(15,TimeUnit.SECONDS)){writer.shutdownNow();com.mojang.logging.LogUtils.getLogger().warn("AntHub: statistics shutdown timed out; last checkpoint is retained");}}catch(InterruptedException interrupted){writer.shutdownNow();Thread.currentThread().interrupt();}}
        store=null;
    }
}
