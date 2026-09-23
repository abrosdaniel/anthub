package dev.abros.anthub.client;

import com.google.gson.*;
import dev.abros.anthub.core.*;
import dev.abros.anthub.network.SkinWire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.io.*;

/** All state belongs to the current connection; no identities leak between servers. */
public final class SkinClient {
 private static final Map<UUID,JsonObject> profiles=new HashMap<>();
 private static final Map<UUID,Long> requested=new HashMap<>();
 private static final Map<String,ResourceLocation> textures=new LinkedHashMap<>();
 private static final Map<String,ByteArrayOutputStream> incoming=new HashMap<>();
 private static final Map<String,Long> fetching=new HashMap<>();
 private static final ArrayDeque<JsonObject> outgoing=new ArrayDeque<>();
 private static final java.util.concurrent.ExecutorService DISK=java.util.concurrent.Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"AntHub skin cache");t.setDaemon(true);return t;});
 private static java.nio.file.Path cache(){return Minecraft.getInstance().gameDirectory.toPath().resolve("anthub/cache/skins");}
 private static void prune(java.nio.file.Path directory){try(var files=java.nio.file.Files.list(directory)){var paths=files.filter(p->p.getFileName().toString().matches("[0-9a-f]{64}\\.png")).sorted(java.util.Comparator.comparingLong((java.nio.file.Path p)->{try{return java.nio.file.Files.getLastModifiedTime(p).toMillis();}catch(Exception e){return 0L;}}).reversed()).toList();long total=0,now=System.currentTimeMillis();for(var p:paths){total+=java.nio.file.Files.size(p);if(total>64L*1024*1024||now-java.nio.file.Files.getLastModifiedTime(p).toMillis()>30L*86400000)java.nio.file.Files.deleteIfExists(p);}}catch(Exception ignored){}}
 private static void persist(String hash,byte[] png){var directory=cache();DISK.execute(()->{try{java.nio.file.Files.createDirectories(directory);java.nio.file.Files.write(directory.resolve(hash+".png"),png);prune(directory);}catch(Exception ignored){}});}

 private static boolean initialized,wasAuthenticated;private static Object connection;private static boolean disabled;private static int epoch;
 static JsonObject library=new JsonObject();static String status="";static boolean busy;
 private static long sentAt;private static String pending="";
 public static void install(){SkinWire.client=SkinClient::receive;NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderNameTagEvent e)->{if(Minecraft.getInstance().screen instanceof SkinsScreen s&&s.isPreview(e.getEntity()))e.setCanRender(net.neoforged.neoforge.common.util.TriState.FALSE);});NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e)->tick());}
 public static boolean available(){var c=Minecraft.getInstance().getConnection();return c!=null&&c.hasChannel(SkinWire.Request.TYPE)&&!disabled;}
 private static void tick(){var c=Minecraft.getInstance().getConnection();if(connection!=c){connection=c;initialized=false;epoch++;var directory=cache();DISK.execute(()->prune(directory));profiles.clear();requested.clear();incoming.clear();fetching.clear();outgoing.clear();library=new JsonObject();status="";busy=false;disabled=false;for(var id:textures.values())Minecraft.getInstance().getTextureManager().release(id);textures.clear();}fetching.entrySet().removeIf(e->{if(System.currentTimeMillis()-e.getValue()>30000){incoming.remove(e.getKey());return true;}return false;});if(!available())return;if(!initialized&&Minecraft.getInstance().player!=null){initialized=true;if(!busy)command("list","",false);}boolean authenticated=AuthClient.available();if(authenticated&&!wasAuthenticated)requested.clear();wasAuthenticated=authenticated;for(int i=0;i<4&&!outgoing.isEmpty();i++)PacketDistributor.sendToServer(new SkinWire.Request(Json.GSON.toJson(outgoing.removeFirst())));if(busy&&System.currentTimeMillis()-sentAt>20000){busy=false;status="Ответ не получен. Обновите список.";changed();}}
 private static void send(JsonObject j){if(available())outgoing.add(j);}
 static void command(String op,String id,boolean slim){if(busy)return;var j=new JsonObject();j.addProperty("op",op);j.addProperty("id",id);j.addProperty("slim",slim);pending=UUID.randomUUID().toString();j.addProperty("request",pending);busy=true;sentAt=System.currentTimeMillis();status="";send(j);}
 static void upload(String name,boolean slim,byte[] bytes){if(busy)return;pending=UUID.randomUUID().toString();busy=true;sentAt=System.currentTimeMillis();status="Загрузка…";var begin=new JsonObject();begin.addProperty("op","begin");begin.addProperty("request",pending);begin.addProperty("name",name);begin.addProperty("slim",slim);begin.addProperty("size",bytes.length);send(begin);for(int offset=0;offset<bytes.length;offset+=4096){var part=new JsonObject();part.addProperty("op","chunk");part.addProperty("request",pending);part.addProperty("offset",offset);part.addProperty("data",Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+4096))));send(part);}}
 private static void changed(){if(Minecraft.getInstance().screen instanceof SkinsScreen s)s.updated();}
 private static void profile(JsonObject j){profiles.put(UUID.fromString(Json.str(j,"owner")),j);String hash=Json.opt(j,"hash","");if(!hash.isEmpty())texture(hash);}
 private static void receive(JsonObject j){try{switch(Json.str(j,"kind")){
  case "disabled"->{disabled=true;busy=false;outgoing.clear();profiles.clear();status="Скины выключены на сервере";changed();}
  case "error"->{if(pending.equals(Json.opt(j,"request",""))){busy=false;outgoing.removeIf(p->pending.equals(Json.opt(p,"request","")));status=Json.opt(j,"text","Ошибка загрузки");changed();}}
  case "library"->{if(!pending.equals(Json.opt(j,"request","")))return;library=j;profile(j.getAsJsonObject("profile"));busy=false;status="";changed();}
  case "appearance"->profile(j);
  case "blob"->{String hash=Json.str(j,"hash");if(!fetching.containsKey(hash))return;int size=j.get("size").getAsInt(),offset=j.get("offset").getAsInt();if(size<33||size>1024*1024)throw new IOException();byte[] part=Base64.getDecoder().decode(Json.str(j,"data"));var buffer=incoming.computeIfAbsent(hash,k->new ByteArrayOutputStream());if(offset!=buffer.size()||part.length>4096||part.length==0||part.length>size-offset)throw new IOException();buffer.writeBytes(part);if(buffer.size()==size){byte[] png=buffer.toByteArray();incoming.remove(hash);fetching.remove(hash);if(!Hashes.sha256(png).equals(hash))throw new IOException();register(hash,png);persist(hash,png);}}
 }}catch(Exception invalid){incoming.clear();}}
 private static ResourceLocation register(String hash,byte[] png)throws IOException{dev.abros.anthub.core.skins.SkinImage.normalize(png,1024*1024);var image=NativeImage.read(png);if(image.getWidth()!=64||image.getHeight()!=64){image.close();throw new IOException("Неверный размер скина");}var id=ResourceLocation.fromNamespaceAndPath("anthub","skins/"+hash);Minecraft.getInstance().getTextureManager().register(id,new DynamicTexture(image));textures.put(hash,id);while(textures.size()>256){var first=textures.keySet().iterator().next();Minecraft.getInstance().getTextureManager().release(textures.remove(first));}return id;}
 static ResourceLocation preview(byte[] png)throws IOException{return register(Hashes.sha256(png),png);}
 static ResourceLocation texture(String hash){if(!hash.matches("[0-9a-f]{64}"))return null;var found=textures.get(hash);if(found==null&&(fetching.containsKey(hash)||fetching.size()<64)&&available()&&System.currentTimeMillis()-fetching.getOrDefault(hash,0L)>15000){fetching.put(hash,System.currentTimeMillis());incoming.remove(hash);int generation=epoch;var path=cache().resolve(hash+".png");DISK.execute(()->{byte[] cached=null;try{if(java.nio.file.Files.size(path)<=1024*1024){var bytes=java.nio.file.Files.readAllBytes(path);if(Hashes.sha256(bytes).equals(hash))cached=bytes;}}catch(Exception ignored){}final byte[] data=cached;Minecraft.getInstance().execute(()->{if(generation!=epoch)return;if(data!=null)try{register(hash,data);fetching.remove(hash);return;}catch(Exception ignored){}var j=new JsonObject();j.addProperty("op","blob");j.addProperty("hash",hash);send(j);});});}return found;}
 public static PlayerSkin skin(UUID owner,PlayerSkin original){if(!available())return original;long now=System.currentTimeMillis();if(now-requested.getOrDefault(owner,0L)>60000){requested.put(owner,now);var j=new JsonObject();j.addProperty("op","appearance");j.addProperty("owner",owner.toString());send(j);}var p=profiles.get(owner);if(p==null)return original;var base=DefaultPlayerSkin.get(owner);String hash=Json.opt(p,"hash","");if(hash.isEmpty())return base;var texture=texture(hash);return texture==null?base:new PlayerSkin(texture,null,null,null,p.get("slim").getAsBoolean()?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE,false);}
 static PlayerSkin skin(UUID owner){var c=Minecraft.getInstance().getConnection();var info=c==null?null:c.getPlayerInfo(owner);return skin(owner,info==null?DefaultPlayerSkin.get(owner):info.getSkin());}
}
