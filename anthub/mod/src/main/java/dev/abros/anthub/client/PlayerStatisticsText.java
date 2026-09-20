package dev.abros.anthub.client;
import com.google.gson.JsonObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
final class PlayerStatisticsText {
    static List<String> lines(JsonObject player){
        List<String> rows=new ArrayList<>();if(!player.has("statistics"))return rows;var s=player.getAsJsonObject("statistics");
        if(s.has("firstJoin"))rows.add("Играет с: "+day(s.get("firstJoin").getAsLong()));
        if(s.has("totalMillis"))rows.add("Время игры: "+duration(s.get("totalMillis").getAsLong()));
        if(s.has("sessionMillis"))rows.add("Сессия: "+duration(s.get("sessionMillis").getAsLong()));
        if(s.has("deaths"))rows.add("Смерти: "+s.get("deaths").getAsLong());
        return rows;
    }
    private static String day(long at){return DateTimeFormatter.ofPattern("dd.MM.yy").format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()));}
    private static String duration(long millis){long minutes=Math.max(0,millis/60000);return minutes/60+" ч "+minutes%60+" мин";}
}
