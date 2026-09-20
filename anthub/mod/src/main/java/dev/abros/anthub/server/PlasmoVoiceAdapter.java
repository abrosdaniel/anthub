package dev.abros.anthub.server;
import java.util.*;
/** Optional Plasmo Voice 2 API. No command-name or nickname based punishment. */
final class PlasmoVoiceAdapter {
 private static Object manager()throws ReflectiveOperationException{Object instance=Class.forName("su.plo.voice.server.ModVoiceServer").getField("INSTANCE").get(null);if(instance==null)throw new IllegalStateException("Plasmo Voice unavailable");return Class.forName("su.plo.voice.api.server.PlasmoVoiceServer").getMethod("getMuteManager").invoke(instance);}
 static boolean available(){try{Object m=manager();Class<?> api=Class.forName("su.plo.voice.api.server.mute.MuteManager");api.getMethod("getMute",UUID.class);Class<?> unit=Class.forName("su.plo.voice.api.server.mute.MuteDurationUnit");try{api.getMethod("mute",UUID.class,UUID.class,long.class,unit,String.class,boolean.class);}catch(NoSuchMethodException older){api.getMethod("mute",UUID.class,UUID.class,long.class,unit,String.class);}return m!=null;}catch(ReflectiveOperationException|LinkageError|RuntimeException missing){return false;}}
 @SuppressWarnings({"unchecked","rawtypes"}) static void mute(UUID target,int minutes,String reason)throws Exception{
  var m=manager();Class<?> api=Class.forName("su.plo.voice.api.server.mute.MuteManager"),unit=Class.forName("su.plo.voice.api.server.mute.MuteDurationUnit");
  if(((Optional<?>)api.getMethod("getMute",UUID.class).invoke(m,target)).isPresent())throw new IllegalArgumentException("голос уже отключён; существующее наказание сохранено");
  Object minute=Enum.valueOf((Class)unit,"MINUTE");Object result;
  try{result=api.getMethod("mute",UUID.class,UUID.class,long.class,unit,String.class,boolean.class).invoke(m,target,null,(long)minutes,minute,reason,false);}catch(NoSuchMethodException older){result=api.getMethod("mute",UUID.class,UUID.class,long.class,unit,String.class).invoke(m,target,null,(long)minutes,minute,reason);}
  if(!(result instanceof Optional<?> applied)||applied.isEmpty())throw new IllegalArgumentException("Plasmo Voice не подтвердил mute");
 }
}
