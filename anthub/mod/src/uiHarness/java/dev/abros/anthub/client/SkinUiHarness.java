package dev.abros.anthub.client;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import dev.abros.anthub.core.Json;
/** Opt-in integration smoke against an isolated local server. */
@EventBusSubscriber(modid="anthub",value=Dist.CLIENT)
public final class SkinUiHarness {
 private static int step;private static long next,deadline;private static String id;private static byte[] png;
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){String output=System.getenv("ANTHUB_SKIN_SMOKE");if(output==null||step<0)return;var mc=Minecraft.getInstance();long now=System.currentTimeMillis();if(now<next)return;
  try{if(deadline==0)deadline=now+150000;if(now>deadline)throw new IllegalStateException("Skin smoke timeout, step "+step+" status "+SkinClient.status);switch(step){
   case 0->{if(!(mc.screen instanceof TitleScreen))return;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString("127.0.0.1:25576"),new ServerData("Skin smoke","127.0.0.1:25576",ServerData.Type.OTHER),false,null);step++;}
   case 1->{if(mc.player==null||!SkinClient.available())return;next=now+1500;step++;}
   case 2->{mc.options.guiScale().set(1);mc.resizeDisplay();SkinsScreen.open(null);step++;}
   case 3->{if(SkinClient.busy)return;if(!SkinClient.library.has("entries"))throw new IllegalStateException(SkinClient.status);var image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,y<16?0xffeeaa88:0xff6622aa);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"PNG",bytes);png=bytes.toByteArray();SkinClient.upload("Проверка скина",false,png);step++;}
   case 4->{if(SkinClient.busy)return;if(SkinClient.library.getAsJsonArray("entries").isEmpty())throw new IllegalStateException(SkinClient.status);id=Json.str(SkinClient.library.getAsJsonObject("profile"),"active");if(id.isEmpty())throw new IllegalStateException("Upload did not activate");step++;next=now+10000;}
   case 5->{if(!mc.player.getSkin().texture().getNamespace().equals("anthub"))return;var dir=new java.io.File(output);dir.mkdirs();net.minecraft.client.Screenshot.grab(dir,"skin-wide.png",mc.getMainRenderTarget(),m->{});SkinClient.command("model",id,true);step++;}
   case 6->{if(SkinClient.busy)return;if(mc.player.getSkin().model()!=net.minecraft.client.resources.PlayerSkin.Model.SLIM)return;next=now+1000;step++;}
   case 7->{var preview=SkinsScreen.class.getDeclaredField("previewPlayer");preview.setAccessible(true);var entity=(net.minecraft.client.player.RemotePlayer)preview.get(mc.screen);for(var part:net.minecraft.world.entity.player.PlayerModelPart.values())if(!entity.isModelPartShown(part))throw new IllegalStateException("Hidden overlay "+part);for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().startsWith("Второй слой:")){b.onPress();break;}if(entity.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT))throw new IllegalStateException("Overlay toggle failed");for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().startsWith("Второй слой:")){b.onPress();break;}var rotation=SkinsScreen.class.getDeclaredField("rotation");rotation.setAccessible(true);rotation.setFloat(mc.screen,1f);for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("Вернуть вид")){b.onPress();break;}if(rotation.getFloat(mc.screen)!=0)throw new IllegalStateException("Preview reset failed");net.minecraft.client.Screenshot.grab(new java.io.File(output),"skin-slim.png",mc.getMainRenderTarget(),m->{});SkinClient.command("rename",id,false,"Новое название");step++;}
   case 8->{if(SkinClient.busy)return;if(!"Новое название".equals(Json.str(SkinClient.library.getAsJsonArray("entries").get(0).getAsJsonObject(),"name")))throw new IllegalStateException("Rename failed");SkinClient.command("select","",false);step++;}
   case 9->{if(SkinClient.busy)return;if(!Json.str(SkinClient.library.getAsJsonObject("profile"),"active").isEmpty())throw new IllegalStateException("Reset failed");SkinClient.command("delete",id,false);step++;}
   case 10->{if(SkinClient.busy)return;if(!SkinClient.library.getAsJsonArray("entries").isEmpty())throw new IllegalStateException("Delete failed");System.out.println("ANTHUB_SKINS_INTEGRATION_OK");if(System.getenv("ANTHUB_SKIN_PICKER_TEST")!=null){var choose=SkinsScreen.class.getDeclaredMethod("chooseFile");choose.setAccessible(true);choose.invoke(mc.screen);next=now+70000;step=11;}else{step=-1;mc.stop();}}
   case 11->{if(mc.player==null||!mc.getConnection().getConnection().isConnected())throw new IllegalStateException("Connection stopped during picker");System.out.println("ANTHUB_SKIN_PICKER_70_SECONDS_OK");step=-1;}
  }}catch(Exception error){error.printStackTrace();System.out.println("ANTHUB_SKINS_INTEGRATION_FAILED");step=-1;mc.stop();}
 }
}
