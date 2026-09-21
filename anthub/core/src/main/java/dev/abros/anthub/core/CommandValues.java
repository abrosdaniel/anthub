package dev.abros.anthub.core;
import java.time.*;
import java.time.format.*;
import java.util.regex.*;
/** Strict, bounded command values. */
public final class CommandValues {
 private CommandValues(){}
 public static long duration(String text){
  if(text.length()>32||!text.matches("(?:[0-9]+h)?(?:[0-9]+m)?")||text.isEmpty())throw new IllegalArgumentException("Укажите время: например 5h30m, 5h или 30m");
  try{var matcher=Pattern.compile("([0-9]+)([hm])").matcher(text);long value=0;while(matcher.find())value=Math.addExact(value,Math.multiplyExact(Long.parseLong(matcher.group(1)),matcher.group(2).equals("h")?3600000L:60000L));return value;}
  catch(ArithmeticException|NumberFormatException ex){throw new IllegalArgumentException("Слишком большое время");}
 }
 public static long date(String text){try{var date=LocalDate.parse(text,DateTimeFormatter.ISO_LOCAL_DATE);if(!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||date.isAfter(LocalDate.now())||date.isBefore(LocalDate.of(1970,1,2)))throw new IllegalArgumentException();return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();}catch(Exception ex){throw new IllegalArgumentException("Укажите дату YYYY-MM-DD, не позднее сегодняшней");}}
 public static String durationText(long millis){return millis/3600000+" ч "+millis/60000%60+" мин";}
 public static String dateText(long millis){return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString();}
}
