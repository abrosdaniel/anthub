package dev.abros.anthub.core;
import com.google.gson.*;import java.util.*;import java.nio.charset.StandardCharsets;
/** Player continuation in stable UUID order; names may change without moving the cursor. */
public final class PlayerCursor {
 private PlayerCursor(){}
 public static String after(String token,String scope){if(token.isEmpty())return "";try{if(token.length()>1024)throw new IllegalArgumentException();var j=Json.parse(new String(Base64.getUrlDecoder().decode(token),StandardCharsets.UTF_8));if(!scope.equals(Json.str(j,"scope")))throw new IllegalArgumentException();return UUID.fromString(Json.str(j,"id")).toString();}catch(Exception ex){throw new CommunityFailure(CommunityFailure.Code.INVALID,"Обновите список игроков");}}
 public static JsonObject page(JsonArray source,String scope){var out=new JsonObject();var entries=new JsonArray();for(int i=0;i<Math.min(20,source.size());i++)entries.add(source.get(i));out.add("entries",entries);String next="";if(source.size()>20){var j=new JsonObject();j.addProperty("scope",scope);j.addProperty("id",Json.str(entries.get(19).getAsJsonObject(),"uuid"));next=Base64.getUrlEncoder().withoutPadding().encodeToString(j.toString().getBytes(StandardCharsets.UTF_8));}out.addProperty("nextCursor",next);return out;}
}
