package dev.abros.anthub.client;
import dev.abros.anthub.core.Json;
import java.util.*;
import net.minecraft.client.Minecraft;
/** One client-wide configuration, deliberately independent of server addresses. */
final class HudSettings {
 static final List<String> BLOCKS=List.of("server","profile","task","event","groups","unread");
 static final List<String> PROFILE_FIELDS=List.of("head","name","prefix","suffix","session","ping");
 static final HudSettings INSTANCE=new HudSettings();
 boolean enabled=true,chat=true,inventory=false,pause=false,debug=false,dimMoving=true,holograms=true,quietHours=false;int quietStart=22,quietEnd=8;float scale=1,opacity=.82f;
 int anchorX=2,anchorY=1,offsetX=8,offsetY=0,toastCount=2;
 final List<String> order=new ArrayList<>(BLOCKS);final Set<String> hidden=new HashSet<>(),muted=new HashSet<>();
 final Set<String> profileHidden=new HashSet<>(List.of("ping"));
 String pinnedTask="";String error="";
 private HudSettings(){try{var file=file();if(java.nio.file.Files.exists(file)){var j=Json.read(file);holograms=bool(j,"holograms",true);dimMoving=bool(j,"dimMoving",true);quietHours=bool(j,"quietHours",false);quietStart=(int)clamp(num(j,"quietStart",22),0,23);quietEnd=(int)clamp(num(j,"quietEnd",8),0,23);enabled=bool(j,"enabled",true);chat=bool(j,"chat",true);inventory=bool(j,"inventory",false);pause=bool(j,"pause",false);debug=bool(j,"debug",false);scale=clamp(num(j,"scale",1),.65f,1.5f);opacity=clamp(num(j,"opacity",.82f),.35f,1);anchorX=(int)clamp(num(j,"anchorX",2),0,2);anchorY=(int)clamp(num(j,"anchorY",1),0,2);offsetX=(int)clamp(num(j,"offsetX",8),-4096,4096);offsetY=(int)clamp(num(j,"offsetY",0),-4096,4096);toastCount=(int)clamp(num(j,"toastCount",2),1,5);if(j.has("order")){order.clear();for(var e:j.getAsJsonArray("order"))if(BLOCKS.contains(e.getAsString())&&!order.contains(e.getAsString()))order.add(e.getAsString());for(String k:BLOCKS)if(!order.contains(k))order.add(k.equals("profile")?Math.min(1,order.size()):order.size(),k);}for(String key:List.of("hidden","muted"))if(j.has(key))for(var e:j.getAsJsonArray(key))(key.equals("hidden")?hidden:muted).add(e.getAsString());if(j.has("profileHidden")){profileHidden.clear();for(var e:j.getAsJsonArray("profileHidden"))if(PROFILE_FIELDS.contains(e.getAsString()))profileHidden.add(e.getAsString());}pinnedTask=Json.opt(j,"pinnedTask","");}}catch(Exception e){error="Не удалось прочитать настройки HUD";}}
 private static java.nio.file.Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("anthub/hud.json");}
 static boolean bool(com.google.gson.JsonObject j,String k,boolean d){return j.has(k)?j.get(k).getAsBoolean():d;}
 static float num(com.google.gson.JsonObject j,String k,float d){return j.has(k)?j.get(k).getAsFloat():d;}
 static float clamp(float v,float min,float max){return Float.isFinite(v)?Math.max(min,Math.min(max,v)):min;}
 void save(){try{var j=new com.google.gson.JsonObject();j.addProperty("holograms",holograms);j.addProperty("dimMoving",dimMoving);j.addProperty("quietHours",quietHours);j.addProperty("quietStart",quietStart);j.addProperty("quietEnd",quietEnd);j.addProperty("enabled",enabled);j.addProperty("chat",chat);j.addProperty("inventory",inventory);j.addProperty("pause",pause);j.addProperty("debug",debug);j.addProperty("scale",scale);j.addProperty("opacity",opacity);j.addProperty("anchorX",anchorX);j.addProperty("anchorY",anchorY);j.addProperty("offsetX",offsetX);j.addProperty("offsetY",offsetY);j.addProperty("toastCount",toastCount);j.add("order",Json.GSON.toJsonTree(order));j.add("hidden",Json.GSON.toJsonTree(hidden));j.add("muted",Json.GSON.toJsonTree(muted));j.add("profileHidden",Json.GSON.toJsonTree(profileHidden));j.addProperty("pinnedTask",pinnedTask);Json.write(file(),j);error="";}catch(Exception e){error="Не удалось сохранить настройки HUD";}}
 String pin(){return pinnedTask;}
 void pin(String id){pinnedTask=id;save();AntHubHud.refresh();}
 static String label(String key){return switch(key){case "profile"->"Мини-профиль";case "server"->"Сервер и онлайн";case "task"->"Моя задача";case "event"->"Ближайшее событие";case "groups"->"Мои объединения";case "unread"->"Непрочитанные";default->key;};}
}
