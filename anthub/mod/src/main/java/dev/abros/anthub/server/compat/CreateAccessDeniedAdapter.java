package dev.abros.anthub.server.compat;
import com.google.gson.*;
import dev.abros.anthub.compat.AccessDeniedBindings;
import dev.abros.anthub.core.Json;
import dev.abros.anthub.core.compat.AccessRepairPlan;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

final class CreateAccessDeniedAdapter implements CompatibilityAdapter {
 private record Preview(UUID owner,UUID network,AccessRepairPlan plan,long expires){}
 private final Map<UUID,Preview> previews=new HashMap<>();
 public String id(){return AccessDeniedBindings.ID;}
 public String status(){return AccessDeniedBindings.status();}
 public void clear(){previews.clear();}
 public JsonObject execute(ServerPlayer actor,JsonObject request,ServerIdentityDirectory identities)throws Exception{
  if(!AccessDeniedBindings.supported())throw new IllegalArgumentException("Адаптер Access Denied: "+status());
  String op=Json.str(request,"op");var result=new JsonObject();
  if(op.equals("lookup")){
   var profile=request.has("name")?identities.byName(Json.str(request,"name")):identities.byId(UUID.fromString(Json.str(request,"uuid")));
   var p=profile.orElseThrow(()->new IllegalArgumentException("Игрок не найден на этом сервере"));result.addProperty("name",p.getName());result.addProperty("uuid",p.getId().toString());return result;
  }
  var network=UUID.fromString(Json.str(request,"network"));
  if(!AccessDeniedBindings.mayAdministrate(network,actor))throw new IllegalArgumentException("Нет права управлять этой логистической сетью");
  long now=System.currentTimeMillis();previews.values().removeIf(p->p.expires()<now);
  if(op.equals("add")){
   var p=identities.byName(Json.str(request,"name")).orElseThrow(()->new IllegalArgumentException("Игрок не найден на этом сервере"));
   var allowed=AccessDeniedBindings.allowed(network);if(!allowed.contains(p.getId())&&allowed.size()>=AccessDeniedBindings.limit())throw new IllegalArgumentException("Достигнут лимит игроков");
   AccessDeniedBindings.add(network,p.getId());AccessDeniedBindings.sync(network,actor);result.addProperty("name",p.getName());result.addProperty("uuid",p.getId().toString());result.addProperty("text","Доступ добавлен: "+p.getName());return result;
  }
  if(op.equals("preview")){
   var plan=AccessRepairPlan.preview(AccessDeniedBindings.allowed(network),identities::verifiedAlias);
   var rows=new JsonArray();plan.replacements().forEach((from,to)->{var row=new JsonObject();row.addProperty("from",from.toString());row.addProperty("to",to.toString());row.addProperty("name",identities.byId(to).map(p->p.getName()).orElse(to.toString()));rows.add(row);});result.add("replacements",rows);
   result.addProperty("text",rows.size()+" подтверждённых замен. Остальные записи сохраняются.");
   if(!rows.isEmpty()){if(previews.size()>=128)throw new IllegalArgumentException("Слишком много предпросмотров");previews.values().removeIf(p->p.owner().equals(actor.getUUID())&&p.network().equals(network));var token=UUID.randomUUID();previews.put(token,new Preview(actor.getUUID(),network,plan,now+120000));result.addProperty("token",token.toString());}return result;
  }
  if(op.equals("apply")){
   var token=UUID.fromString(Json.str(request,"token"));var preview=previews.get(token);
   if(preview==null||!preview.owner().equals(actor.getUUID())||!preview.network().equals(network))throw new IllegalArgumentException("Предпросмотр истёк. Проверьте список заново");
   // A verified alias may have been unlinked since the preview.
   for(var entry:preview.plan().replacements().entrySet())if(!identities.verifiedAlias(entry.getKey()).filter(entry.getValue()::equals).isPresent())throw new IllegalArgumentException("Связь аккаунта изменилась. Повторите предпросмотр");
   AccessDeniedBindings.replace(network,preview.plan().before(),preview.plan().after());previews.remove(token);AccessDeniedBindings.sync(network,actor);result.addProperty("text","Исправлено записей: "+preview.plan().replacements().size());return result;
  }
  throw new IllegalArgumentException("Неизвестное действие адаптера");
 }
}
