package dev.abros.anthub.core.skins;
import dev.abros.anthub.core.Json;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Only fixed Mojang endpoints and the official texture host may be fetched. */
public final class MojangSkins {
 public record Result(byte[] png,boolean slim){}
 private static byte[] get(String address,int limit)throws IOException{
  var url=URI.create(address);if(!url.getScheme().equals("https")||!Set.of("api.mojang.com","sessionserver.mojang.com","textures.minecraft.net").contains(url.getHost())||url.getPort()!=-1||url.getUserInfo()!=null)throw new IOException("Invalid Mojang URL");
  var c=(HttpURLConnection)url.toURL().openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setInstanceFollowRedirects(false);
  try{int status=c.getResponseCode();if(status==204||status==404)return null;if(status!=200)throw new IOException("Mojang HTTP "+status);try(var in=c.getInputStream()){byte[] bytes=in.readNBytes(limit+1);if(bytes.length>limit)throw new IOException("Mojang response too large");return bytes;}}finally{c.disconnect();}
 }
 public static Result find(String nickname,UUID official)throws Exception{
  String id;if(official!=null)id=official.toString().replace("-","");else{if(!nickname.matches("[A-Za-z0-9_]{1,16}"))return new Result(null,false);var bytes=get("https://api.mojang.com/users/profiles/minecraft/"+nickname,16384);if(bytes==null)return new Result(null,false);id=Json.str(Json.parse(new String(bytes,StandardCharsets.UTF_8)),"id");}
  if(!id.matches("[0-9a-fA-F]{32}"))throw new IOException("Invalid Mojang profile");var bytes=get("https://sessionserver.mojang.com/session/minecraft/profile/"+id,32768);if(bytes==null)return new Result(null,false);
  for(var property:Json.parse(new String(bytes,StandardCharsets.UTF_8)).getAsJsonArray("properties")){var p=property.getAsJsonObject();if(!Json.str(p,"name").equals("textures"))continue;var body=Json.parse(new String(Base64.getDecoder().decode(Json.str(p,"value")),StandardCharsets.UTF_8));var textures=body.getAsJsonObject("textures");if(!textures.has("SKIN"))return new Result(null,false);var skin=textures.getAsJsonObject("SKIN");String url=Json.str(skin,"url");if(url.startsWith("http://textures.minecraft.net/"))url="https"+url.substring(4);var uri=URI.create(url);if(!"textures.minecraft.net".equals(uri.getHost())||!uri.getPath().matches("/texture/[0-9a-fA-F]+"))throw new IOException("Invalid texture URL");return new Result(get(url,1024*1024),skin.has("metadata")&&"slim".equals(Json.opt(skin.getAsJsonObject("metadata"),"model","")));}
  return new Result(null,false);
 }
}
