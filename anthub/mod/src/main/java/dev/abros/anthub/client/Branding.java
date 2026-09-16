package dev.abros.anthub.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
public final class Branding {

 private record Texture(ResourceLocation id,int width,int height){}
 private static final java.util.Map<String,Texture> textures=new java.util.LinkedHashMap<>(16,0.75f,true);
 public static int accent(dev.abros.anthub.core.Manifest manifest){try{return 0xFF000000|Integer.parseInt(manifest.json().getAsJsonObject("theme").get("accent").getAsString().substring(1),16);}catch(Exception ignored){return 0xFFE2BE75;}}
 public static void serverIcon(net.minecraft.client.multiplayer.ServerData server,GuiGraphics g,int x,int y,int size){
  byte[] bytes=server.getIconBytes();if(bytes==null){g.fill(x,y,x+size,y+size,0xFF555555);return;}String hash=dev.abros.anthub.core.Hashes.sha256(bytes);Texture texture=textures.get(hash);
  if(texture==null)try{var image=com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(bytes));if(image.getWidth()!=64||image.getHeight()!=64){image.close();return;}var location=ResourceLocation.fromNamespaceAndPath("anthub","server/"+hash);Minecraft.getInstance().getTextureManager().register(location,new net.minecraft.client.renderer.texture.DynamicTexture(image));texture=new Texture(location,image.getWidth(),image.getHeight());textures.put(hash,texture);if(textures.size()>64){var first=textures.entrySet().iterator();var expired=first.next().getValue();first.remove();Minecraft.getInstance().getTextureManager().release(expired.id());}}catch(java.io.IOException ignored){return;}
  g.blit(texture.id(),x,y,size,size,0f,0f,texture.width(),texture.height(),texture.width(),texture.height());
 }
 public static void icon(GuiGraphics graphics,int x,int y,int size){
  int pixels=(int)Math.ceil(size*Minecraft.getInstance().getWindow().getGuiScale());
  int resolution=pixels<=24?24:pixels<=32?32:pixels<=64?64:256;
  var icon=ResourceLocation.fromNamespaceAndPath("anthub","textures/gui/icon_"+resolution+".png");
  graphics.blit(icon,x,y,size,size,0f,0f,resolution,resolution,resolution,resolution);
 }
}
