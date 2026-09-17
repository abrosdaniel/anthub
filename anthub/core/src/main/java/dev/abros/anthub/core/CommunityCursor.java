package dev.abros.anthub.core;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
/** Opaque continuation bound to the actor and filters; never an authorization grant. */
public record CommunityCursor(long sequence,long time,long ceiling,String scope) {
 public String encode(){return Base64.getUrlEncoder().withoutPadding().encodeToString(Json.GSON.toJson(this).getBytes(StandardCharsets.UTF_8));}
 public static CommunityCursor decode(String token,String scope){try{if(token.length()>1024)throw new IllegalArgumentException();var c=Json.GSON.fromJson(new String(Base64.getUrlDecoder().decode(token),StandardCharsets.UTF_8),CommunityCursor.class);if(c==null||!scope.equals(c.scope)||c.sequence<0||c.time<0||c.ceiling<c.sequence)throw new IllegalArgumentException();return c;}catch(Exception ex){throw new CommunityFailure(CommunityFailure.Code.INVALID,"Список изменился. Обновите его.");}}
}
