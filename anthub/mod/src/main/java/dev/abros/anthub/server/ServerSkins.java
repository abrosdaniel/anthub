package dev.abros.anthub.server;
import dev.abros.anthub.core.*;
import dev.abros.anthub.core.skins.*;
import dev.abros.anthub.network.SkinWire;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.concurrent.*;
import java.io.ByteArrayOutputStream;
public final class ServerSkins {
 private static volatile SkinStore store;private static volatile SkinSettings settings;
 private static final ExecutorService FALLBACK=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"AntHub Mojang skins");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardPolicy());
 private static final ExecutorService WORK=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"AntHub skins");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private record Upload(Object connection,String request,String name,boolean slim,int size,ByteArrayOutputStream bytes,long expires){}
 private static final Map<UUID,Upload> uploads=new HashMap<>();private static final Map<UUID,long[]> rates=new HashMap<>();private static final Map<UUID,Long> uploadTimes=new HashMap<>();
 private static final Map<UUID,Long> fallbackAttempts=new ConcurrentHashMap<>();
 public static void install(){SkinWire.server=ServerSkins::request;
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartingEvent e)->{settings=ServerDatabase.settings().skins();store=new SkinStore(ServerDatabase.get(),settings);uploads.clear();rates.clear();uploadTimes.clear();fallbackAttempts.clear();});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e)->{UUID id=e.getEntity().getUUID();uploads.remove(id);rates.remove(id);uploadTimes.remove(id);});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e)->{store=null;uploads.clear();rates.clear();uploadTimes.clear();fallbackAttempts.clear();});
 }
 private static void send(ServerPlayer p,JsonObject j){if(p.connection.getConnection().isConnected()&&AuthServer.authenticated(p))PacketDistributor.sendToPlayer(p,new SkinWire.Response(Json.GSON.toJson(j)));}
 private static void error(ServerPlayer p,String request,String text){var j=new JsonObject();j.addProperty("kind","error");j.addProperty("request",request);j.addProperty("text",text);send(p,j);}
 private static JsonObject config(){var j=new JsonObject();j.addProperty("enabled",settings.enabled());j.addProperty("maxBytes",settings.maxBytes());j.addProperty("limit",settings.maxSkinsPerPlayer());return j;}
 private static void request(JsonObject j,net.neoforged.neoforge.network.handling.IPayloadContext c){
  if(!(c.player() instanceof ServerPlayer p)||store==null||!AuthServer.authenticated(p)||!ServerIntegration.supports(p,"menu"))return;
  String request=Json.opt(j,"request",""),op=Json.opt(j,"op","");long now=System.currentTimeMillis();var rate=rates.computeIfAbsent(p.getUUID(),id->new long[]{now,0});if(now-rate[0]>1000){rate[0]=now;rate[1]=0;}if(++rate[1]>80){error(p,request,"Слишком много запросов");return;}
  if(!settings.enabled()){var out=config();out.addProperty("kind","disabled");out.addProperty("request",request);send(p,out);return;}
  final SkinStore current=store;final Object connection=p.connection.getConnection();final var server=p.server;
  try{
   byte[] uploaded=null;
   if(op.equals("begin")){uploads.entrySet().removeIf(e->e.getValue().expires<now);int size=j.get("size").getAsInt();if(size<33||size>settings.maxBytes())throw new IllegalArgumentException("Размер PNG превышает лимит");if(now-uploadTimes.getOrDefault(p.getUUID(),0L)<3000)throw new IllegalArgumentException("Подождите перед загрузкой");int reserved=uploads.values().stream().mapToInt(Upload::size).sum();if(reserved+size>32*1024*1024)throw new IllegalArgumentException("Сервер занят загрузками");String name=Json.str(j,"name");if(name.length()>40||name.isBlank())throw new IllegalArgumentException("Название: 1–40 символов");uploads.put(p.getUUID(),new Upload(connection,request,name,j.get("slim").getAsBoolean(),size,new ByteArrayOutputStream(size),now+60000));uploadTimes.put(p.getUUID(),now);return;}
   if(op.equals("chunk")){var u=uploads.get(p.getUUID());if(u==null||u.connection!=connection||!u.request.equals(request)||now>u.expires)throw new IllegalArgumentException("Загрузка истекла");byte[] part=Base64.getDecoder().decode(Json.str(j,"data"));if(part.length>4096||part.length==0||j.get("offset").getAsInt()!=u.bytes.size()||part.length>u.size-u.bytes.size()){uploads.remove(p.getUUID());throw new IllegalArgumentException("Неверная часть загрузки");}u.bytes.writeBytes(part);if(u.bytes.size()<u.size)return;uploads.remove(p.getUUID());uploaded=u.bytes.toByteArray();j=new JsonObject();j.addProperty("name",u.name);j.addProperty("slim",u.slim);op="upload";}
   final String operation=op;final JsonObject input=j;final byte[] bytes=uploaded;
   UUID lookup=op.equals("appearance")?UUID.fromString(Json.str(j,"owner")):p.getUUID();var online=server.getPlayerList().getPlayer(lookup);String nickname=online==null?null:online.getGameProfile().getName();boolean official=server.usesAuthentication();
   WORK.execute(()->{try{
    if(store!=current||!server.submit(()->p.connection.getConnection()==connection&&p.connection.getConnection().isConnected()&&AuthServer.authenticated(p)).get(2,TimeUnit.SECONDS))return;
    switch(operation){
     case "blob"->{String hash=Json.str(input,"hash");byte[] png=current.bytes(hash);var parts=new ArrayList<JsonObject>();for(int offset=0;offset<png.length;offset+=4096){var out=new JsonObject();out.addProperty("kind","blob");out.addProperty("hash",hash);out.addProperty("offset",offset);out.addProperty("size",png.length);out.addProperty("data",Base64.getEncoder().encodeToString(Arrays.copyOfRange(png,offset,Math.min(png.length,offset+4096))));parts.add(out);}server.execute(()->{if(store==current)for(var part:parts)send(p,part);});return;}
     case "appearance","list"->{var activeSettings=settings;FALLBACK.execute(()->{try{if(store!=current)return;if(!refreshFallback(current,activeSettings,lookup,nickname,official))return;var updated=current.appearance(lookup);updated.addProperty("kind","appearance");server.execute(()->{if(store==current)for(var other:server.getPlayerList().getPlayers())if(ServerIntegration.supports(other,"skins"))send(other,updated);});}catch(Exception ignored){}});}
     case "upload"->current.upload(p.getUUID(),Json.str(input,"name"),input.get("slim").getAsBoolean(),bytes);
     case "rename"->current.rename(p.getUUID(),Json.str(input,"id"),Json.str(input,"name"));
     case "select","delete","model"->current.change(p.getUUID(),operation,Json.opt(input,"id",""),input.has("slim")&&input.get("slim").getAsBoolean());
     default->throw new IllegalArgumentException("Неизвестное действие");
    }
    boolean mutation=Set.of("upload","select","delete","model").contains(operation);var appearance=current.appearance(lookup);appearance.addProperty("kind","appearance");var out=operation.equals("appearance")?appearance:current.library(p.getUUID());if(!operation.equals("appearance"))out.addProperty("kind","library");out.addProperty("request",request);
    server.execute(()->{if(store!=current)return;send(p,out);if(mutation)for(var other:server.getPlayerList().getPlayers())if(ServerIntegration.supports(other,"skins"))send(other,appearance);});
   }catch(Exception failure){server.execute(()->{if(store==current)error(p,request,failure instanceof IllegalArgumentException||failure instanceof java.io.IOException?Objects.toString(failure.getMessage(),"Не удалось обработать скин"):"Не удалось обработать скин. Повторите позже.");});}});
  }catch(Exception failure){error(p,request,failure instanceof RejectedExecutionException?"Сервер занят":Objects.toString(failure.getMessage(),"Ошибка скина"));}
 }
 private static boolean refreshFallback(SkinStore s,SkinSettings configuration,UUID owner,String name,boolean onlineMode)throws Exception{
  String mode=configuration.mojangFallback();UUID official=null;if(mode.equals("UUID")){official=ServerDatabase.get().transaction(()->{try(var q=ServerDatabase.get().connection().prepareStatement("SELECT official FROM auth_accounts WHERE uuid=?")){q.setString(1,owner.toString());try(var r=q.executeQuery()){return r.next()&&r.getString(1)!=null?UUID.fromString(r.getString(1)):null;}}});if(official==null&&onlineMode)official=owner;}
  if(name==null&&!mode.equals("false"))return false;String source=mode+":"+(mode.equals("UUID")?official:name);long now=System.currentTimeMillis();if(now-s.checked(owner,source)<86400000)return false;
  if(mode.equals("false")||mode.equals("UUID")&&official==null){s.fallback(owner,source,null,false,now);return true;}
  if(now-fallbackAttempts.getOrDefault(owner,0L)<60000)return false;fallbackAttempts.put(owner,now);
  try{var result=MojangSkins.find(name,official);s.fallback(owner,source,result.png(),result.slim(),now);return true;}catch(Exception unavailable){/* Keep last verified image. */return false;}
 }
}
