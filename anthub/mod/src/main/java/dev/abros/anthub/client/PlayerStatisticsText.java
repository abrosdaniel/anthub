package dev.abros.anthub.client;
import com.google.gson.JsonObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
final class PlayerStatisticsText {
    static List<String> lines(JsonObject player){
        List<String> rows=new ArrayList<>();for(String field:List.of("about","interests")){String text=dev.abros.anthub.core.Json.opt(player,field,"");if(!text.isBlank())for(var line:net.minecraft.client.Minecraft.getInstance().font.split(net.minecraft.network.chat.Component.literal((field.equals("interests")?"Интересы: ":"")+text),178)){if(rows.size()<3)rows.add(plain(line));}}if(!player.has("statistics"))return rows;var s=player.getAsJsonObject("statistics");
        if(s.has("firstJoin"))rows.add("Играет с: "+day(s.get("firstJoin").getAsLong()));
        if(s.has("totalMillis"))rows.add("Время игры: "+duration(s.get("totalMillis").getAsLong()));
        if(s.has("sessionMillis"))rows.add("Сессия: "+duration(s.get("sessionMillis").getAsLong()));
        if(s.has("deaths"))rows.add("Смерти: "+s.get("deaths").getAsLong());
        return rows;
    }
    private static String plain(net.minecraft.util.FormattedCharSequence line){var text=new StringBuilder();line.accept((i,style,c)->{text.appendCodePoint(c);return true;});return text.toString();}
    private static String day(long at){return DateTimeFormatter.ofPattern("dd.MM.yy").format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()));}
    private static String duration(long millis){long minutes=Math.max(0,millis/60000);return minutes/60+" ч "+minutes%60+" мин";}
}
