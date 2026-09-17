package dev.abros.anthub.core;

import com.google.gson.*;
/** Optional user-published location. Never trusts a client-provided dimension or numeric coercion. */
public record CommunityLocation(String name,String dimension,int x,int y,int z,boolean membersOnly) {
 public static CommunityLocation read(JsonObject j){
  Json.keys(j,"name","dimension","x","y","z","membersOnly");
  String name=Json.str(j,"name").strip(),dimension=Json.str(j,"dimension");
  if(name.isEmpty()||name.length()>80||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Название места: от 1 до 80 символов");
  if(dimension.length()>160||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||dimension.contains(".."))throw new IllegalArgumentException("Некорректное измерение");
  boolean privatePlace=false;if(j.has("membersOnly")){if(!j.get("membersOnly").isJsonPrimitive()||!j.getAsJsonPrimitive("membersOnly").isBoolean())throw new IllegalArgumentException("Некорректная видимость места");privatePlace=j.get("membersOnly").getAsBoolean();}
  return new CommunityLocation(name,dimension,coordinate(j,"x",30000000),coordinate(j,"y",2048),coordinate(j,"z",30000000),privatePlace);
 }
 private static int coordinate(JsonObject j,String key,int bound){try{var v=j.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();int n=v.getAsBigDecimal().intValueExact();if(n < -bound||n>bound)throw new IllegalArgumentException();return n;}catch(Exception ex){throw new IllegalArgumentException("Некорректная координата "+key.toUpperCase());}}
 public JsonObject json(){return Json.GSON.toJsonTree(this).getAsJsonObject();}
 public String coordinates(){return x+" "+y+" "+z+" · "+dimension;}
}
