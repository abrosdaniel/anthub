package dev.abros.anthub.core;
import java.time.*;
import java.time.format.DateTimeFormatter;
/** Server presence timestamp displayed in the client's selected time zone. */
public final class LastActivity {
 private LastActivity(){}
 public static String text(long at,long now,ZoneId zone){
  if(at<=0)return "Был давно";
  long age=Math.max(0,now-at),minutes=age/60000;
  if(minutes==0)return "Был только что";
  if(minutes<60)return "Был "+minutes+" "+word(minutes,"минуту","минуты","минут")+" назад";
  long hours=minutes/60;if(hours<24)return "Был "+hours+" "+word(hours,"час","часа","часов")+" назад";
  if(age>=30L*86400000)return "Был давно";
  return "Был "+DateTimeFormatter.ofPattern("dd.MM.yyyy 'в' HH:mm").format(Instant.ofEpochMilli(at).atZone(zone));
 }
 private static String word(long n,String one,String few,String many){long v=n%100;if(v>=11&&v<=14)return many;return switch((int)(n%10)){case 1->one;case 2,3,4->few;default->many;};}
}
