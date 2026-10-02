package dev.abros.anthub.core;
import java.util.Set;
/** Per-category overrides never rewrite global notification preferences. */
public final class HudDeliveryPolicy {
 private HudDeliveryPolicy(){}
 public static boolean allowed(Set<String> preferences,String category,String event,boolean defaultAllowed){
  if(preferences.contains(category)||preferences.contains(category+":"+event))return false;
  if(preferences.contains("allow:"+category+":"+event))return true;
  if(preferences.contains("event:"+event))return false;
  return defaultAllowed||preferences.contains("allow:"+event);
 }
}
