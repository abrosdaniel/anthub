package dev.abros.anthub.client;
import com.google.gson.JsonObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
final class PlayerStatisticsText {
    static List<String> lines(JsonObject player){return lines(player,178);}
    static List<String> lines(JsonObject player,int width){var rows=new ArrayList<>(biography(player,width));rows.addAll(statistics(player));return rows;}
    static List<String> biography(JsonObject player,int width){var rows=new ArrayList<String>();for(String field:List.of("about")){String text=dev.abros.anthub.core.Json.opt(player,field,"");if(text.isBlank())continue;int count=0;for(var line:net.minecraft.client.Minecraft.getInstance().font.split(net.minecraft.network.chat.Component.literal((field.equals("interests")?"Интересы: ":"О себе: ")+text),width)){if(count++>=2)break;rows.add(plain(line));}}return rows;}
    static List<String> statistics(JsonObject player){var rows=new ArrayList<String>();if(!player.has("statistics"))return rows;var s=player.getAsJsonObject("statistics");
        if(s.has("firstJoin"))rows.add("На сервере с: "+day(s.get("firstJoin").getAsLong()));
        if(s.has("totalMillis"))rows.add("Время игры: "+duration(s.get("totalMillis").getAsLong()));
        if(s.has("sessionMillis"))rows.add("Сессия: "+duration(s.get("sessionMillis").getAsLong()));
        if(s.has("deaths"))rows.add("Смерти: "+s.get("deaths").getAsLong());return rows;}
    static List<String> summary(JsonObject player){var rows=new ArrayList<String>();if(player.has("statistics")){var s=player.getAsJsonObject("statistics");if(s.has("totalMillis"))rows.add("Время игры: "+duration(s.get("totalMillis").getAsLong()));if(s.has("firstJoin"))rows.add("На сервере с "+day(s.get("firstJoin").getAsLong()));if(s.has("deaths"))rows.add("Смерти: "+s.get("deaths").getAsLong());}return rows;}
    static String activity(JsonObject player){
        if(player.has("online")&&player.get("online").getAsBoolean())return "В сети";
        long at=player.has("seen")?player.get("seen").getAsLong():0;
        if(player.has("statistics")&&player.getAsJsonObject("statistics").has("lastActivity"))at=Math.max(at,player.getAsJsonObject("statistics").get("lastActivity").getAsLong());
        return dev.abros.anthub.core.LastActivity.text(at,System.currentTimeMillis(),AccessibilityScreen.zone());
    }
    private static String plain(net.minecraft.util.FormattedCharSequence line){var text=new StringBuilder();line.accept((i,style,c)->{text.appendCodePoint(c);return true;});return text.toString();}
    private static String day(long at){return DateTimeFormatter.ofPattern("dd.MM.yy").format(Instant.ofEpochMilli(at).atZone(AccessibilityScreen.zone()));}
    private static String duration(long millis){long minutes=Math.max(0,millis/60000);return minutes/60+" ч "+minutes%60+" мин";}
}
