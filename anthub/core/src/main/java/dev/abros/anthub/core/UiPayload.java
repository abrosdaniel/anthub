package dev.abros.anthub.core;
import com.google.gson.JsonObject;
/** Compare displayed response data independently of request correlation IDs. */
public final class UiPayload {
 private UiPayload(){}
 public static boolean same(JsonObject first,JsonObject second){if(first==second)return true;if(first==null||second==null)return false;int size=first.size()-(first.has("request")?1:0);if(size!=second.size()-(second.has("request")?1:0))return false;for(var field:first.entrySet())if(!field.getKey().equals("request")&&!field.getValue().equals(second.get(field.getKey())))return false;return true;}
}
