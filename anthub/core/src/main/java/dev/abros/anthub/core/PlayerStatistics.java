package dev.abros.anthub.core;

import com.google.gson.*;
import java.util.*;

/** Idempotent cumulative checkpoints. No world counters, addresses or credentials. */
public final class PlayerStatistics {
    public record Settings(boolean firstJoin,boolean lastActivity,boolean totalTime,boolean session,boolean deaths){
        public static Settings defaults(){return new Settings(true,true,true,true,true);}
        public static Settings read(JsonObject json){
            for(String key:json.keySet())if(!Set.of("firstJoin","lastActivity","totalTime","session","deaths").contains(key))throw new IllegalArgumentException("Unknown player statistics setting: "+key);
            return new Settings(flag(json,"firstJoin"),flag(json,"lastActivity"),flag(json,"totalTime"),flag(json,"session"),flag(json,"deaths"));
        }
        private static boolean flag(JsonObject j,String key){if(!j.has(key))return true;var v=j.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Invalid statistics setting: "+key);return v.getAsBoolean();}
    }
    public record Checkpoint(UUID player,UUID session,long started,long at,long elapsedMillis,long deaths,boolean online){
        public Checkpoint{Objects.requireNonNull(player);Objects.requireNonNull(session);if(started<0||at<started||elapsedMillis<0||deaths<0)throw new IllegalArgumentException("Invalid statistics checkpoint");}
    }
    private final PgDatabase db;
    private final Settings settings;
    public PlayerStatistics(PgDatabase db,Settings settings){this.db=db;this.settings=settings;}
    public Settings settings(){return settings;}
    public void checkpoint(Checkpoint c)throws Exception{
        db.communityTransaction(()->{
            db.lock("player-statistics:"+c.player());JsonObject data=new JsonObject();
            try(var q=db.connection().prepareStatement("SELECT body FROM records WHERE namespace='player-statistics' AND id=?")){q.setString(1,c.player().toString());try(var row=q.executeQuery()){if(row.next())data=Json.parse(row.getString(1));}}
            long previousStart=number(data,"sessionStarted",-1);
            if(c.started()<previousStart)return null;
            boolean same=c.session().toString().equals(Json.opt(data,"sessionId",""));
            // A completed session cannot be revived by a delayed checkpoint.
            if(same&&data.has("ended")&&data.get("ended").getAsBoolean())return null;
            long previousElapsed=same?number(data,"checkpointMillis",0):0,previousDeaths=same?number(data,"checkpointDeaths",0):0;
            if(same&&(c.elapsedMillis()<previousElapsed||c.deaths()<previousDeaths))return null;
            if(settings.firstJoin()&&!data.has("firstJoin"))data.addProperty("firstJoin",c.started());
            if(settings.lastActivity())data.addProperty("lastActivity",Math.max(c.at(),number(data,"lastActivity",0)));
            if(settings.totalTime()){
                if(!data.has("timeSince"))data.addProperty("timeSince",c.started());
                data.addProperty("totalMillis",Math.addExact(number(data,"totalMillis",0),c.elapsedMillis()-previousElapsed));
            }
            if(settings.deaths()){
                if(!data.has("deathsSince"))data.addProperty("deathsSince",c.started());
                data.addProperty("deaths",Math.addExact(number(data,"deaths",0),c.deaths()-previousDeaths));
            }
            data.addProperty("sessionId",c.session().toString());data.addProperty("sessionStarted",c.started());
            data.addProperty("checkpointMillis",c.elapsedMillis());data.addProperty("checkpointDeaths",c.deaths());data.addProperty("ended",!c.online());
            try(var q=db.connection().prepareStatement("INSERT INTO records(namespace,id,body) VALUES('player-statistics',?,?::jsonb) ON CONFLICT(namespace,id) DO UPDATE SET body=excluded.body")){q.setString(1,c.player().toString());q.setString(2,Json.GSON.toJson(data));q.executeUpdate();}return null;
        });
    }
    /** Server-only eligibility check, independent of public field visibility. */
    public long recordedMillis(UUID player)throws Exception{return db.communityTransaction(()->{try(var q=db.connection().prepareStatement("SELECT coalesce((body->>'totalMillis')::bigint,0) FROM records WHERE namespace='player-statistics' AND id=?")){q.setString(1,player.toString());try(var row=q.executeQuery()){return row.next()?row.getLong(1):0L;}}});}
    /** One bounded query for a page; disabled fields never leave the server. */
    public Map<UUID,JsonObject> read(Collection<UUID> ids,Map<UUID,Long> liveMillis)throws Exception{
        if(ids.size()>100)throw new IllegalArgumentException("Statistics page is too large");
        return db.communityTransaction(()->{
            Map<UUID,JsonObject> result=new HashMap<>();
            try(var q=db.connection().prepareStatement("SELECT id,body FROM records WHERE namespace='player-statistics' AND id=ANY(?)")){
                var array=db.connection().createArrayOf("text",ids.stream().map(UUID::toString).toArray());
                try{q.setArray(1,array);try(var rows=q.executeQuery()){while(rows.next()){
                    UUID id=UUID.fromString(rows.getString(1));var saved=Json.parse(rows.getString(2));var view=new JsonObject();
                    if(settings.firstJoin())copy(saved,view,"firstJoin");if(settings.lastActivity())copy(saved,view,"lastActivity");
                    if(settings.totalTime()){copy(saved,view,"totalMillis");copy(saved,view,"timeSince");}
                    if(settings.deaths()){copy(saved,view,"deaths");copy(saved,view,"deathsSince");}
                    result.put(id,view);
                }}}finally{array.free();}
            }
            if(settings.session())for(UUID id:ids)if(liveMillis.containsKey(id))result.computeIfAbsent(id,k->new JsonObject()).addProperty("sessionMillis",Math.max(0,liveMillis.get(id)));
            return result;
        });
    }
    private static long number(JsonObject j,String key,long fallback){return j.has(key)?j.get(key).getAsLong():fallback;}
    private static void copy(JsonObject from,JsonObject to,String key){if(from.has(key))to.add(key,from.get(key));}
}
