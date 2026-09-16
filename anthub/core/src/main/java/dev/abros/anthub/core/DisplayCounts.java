package dev.abros.anthub.core;
import com.google.gson.JsonObject;
/** Optional UI counters; malformed values never weaken strict manifest parsing. */
public final class DisplayCounts {
 private DisplayCounts(){}
 public static String text(JsonObject object,String key,String fallback){
  var value=object.get(key);
  if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())return fallback;
  try{long count=value.getAsBigDecimal().longValueExact();return count<0?fallback:Long.toString(count);}
  catch(ArithmeticException|NumberFormatException error){return fallback;}
 }
}
