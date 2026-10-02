package dev.abros.anthub.compattests;
import dev.abros.anthub.compat.AccessDeniedBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.*;
@EventBusSubscriber(modid="anthub_compat_tests",value=Dist.CLIENT)
public final class CompatibilityClientHarness {
 private static boolean done,connecting;private static int stage;private static long deadline;private static final UUID NETWORK=UUID.fromString("b5d70e3e-704c-4cc5-9829-cfa7c55fcff3"),POP=UUID.fromString("1edbd7a9-088f-34ae-8786-d56a6299d1df");
 @SubscribeEvent public static void render(ScreenEvent.Render.Post event){String mode=System.getenv("ANTHUB_COMPAT_SMOKE");if(mode==null||done||!(event.getScreen() instanceof TitleScreen))return;if(mode.equals("bridge")){if(!connecting){connecting=true;deadline=System.currentTimeMillis()+60000;var mc=Minecraft.getInstance();net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25579"),new net.minecraft.client.multiplayer.ServerData("Compatibility test","127.0.0.1:25579",net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);}return;}done=true;var mc=Minecraft.getInstance();try{
  if(mode.equals("absent")){if(AccessDeniedBindings.supported())throw new AssertionError("Unexpected optional mod");Class.forName("dev.abros.anthub.client.CompatibilityClient");}
  else {if(!AccessDeniedBindings.supported())throw new AssertionError(AccessDeniedBindings.status());var screen=AccessDeniedBindings.type("screen.AccessControlScreen");var cache=AccessDeniedBindings.type("screen.AccessControlScreen$ProfileCache");
   if(Arrays.stream(screen.getDeclaredMethods()).noneMatch(m->m.getName().contains("anthub$serverIdentity")))throw new AssertionError("Screen mixin missing");
   if(Arrays.stream(cache.getDeclaredMethods()).noneMatch(m->m.getName().contains("anthub$publicProfile")))throw new AssertionError("Profile mixin missing");
   cache.getConstructor(com.mojang.authlib.GameProfile.class,net.minecraft.resources.ResourceLocation.class);
   var id=UUID.randomUUID();var state=(Map<UUID,Set<UUID>>)AccessDeniedBindings.type("AccessDenied").getField("AllowedPlayersClientState").get(null);state.put(id,new HashSet<>());
   var view=(net.minecraft.client.gui.screens.Screen)screen.getConstructor(UUID.class,net.minecraft.client.gui.screens.Screen.class).newInstance(id,mc.screen);if(view.getTitle()==null)throw new AssertionError("Missing title");
  }
  System.out.println("ANTHUB_COMPAT_CLIENT_OK "+mode);
 }catch(Throwable error){System.out.println("ANTHUB_COMPAT_CLIENT_FAILED "+mode);error.printStackTrace();}finally{mc.stop();}}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){if(!"bridge".equals(System.getenv("ANTHUB_COMPAT_SMOKE"))||!connecting||done)return;var mc=Minecraft.getInstance();try{
  if(System.currentTimeMillis()>deadline)throw new AssertionError("Bridge timeout at stage "+stage);
  if(stage==0){if(mc.level==null||mc.player==null||mc.screen!=null)return;if(!((Map<?,?>)AccessDeniedBindings.type("AccessDenied").getField("AllowedPlayersClientState").get(null)).containsKey(NETWORK))return;if(!dev.abros.anthub.client.CompatibilityClient.active())throw new AssertionError("Capability not negotiated");var screen=AccessDeniedBindings.type("screen.AccessControlScreen");mc.setScreen((net.minecraft.client.gui.screens.Screen)screen.getConstructor(UUID.class,net.minecraft.client.gui.screens.Screen.class).newInstance(NETWORK,null));var field=screen.getDeclaredField("playerUsernameBox");field.setAccessible(true);((net.minecraft.client.gui.components.EditBox)field.get(mc.screen)).setValue("POPOSHA");var add=screen.getDeclaredMethod("submitToAdd");add.setAccessible(true);add.invoke(mc.screen);stage=1;return;}
  var state=(Map<UUID,Set<UUID>>)AccessDeniedBindings.type("AccessDenied").getField("AllowedPlayersClientState").get(null);var entries=state.getOrDefault(NETWORK,Set.of());
  if(stage==1){if(!entries.contains(POP))return;var cache=AccessDeniedBindings.type("screen.AccessControlScreen$ProfileCache").getMethod("get",UUID.class,Minecraft.class).invoke(null,POP,mc);var profile=(com.mojang.authlib.GameProfile)cache.getClass().getMethod("profile").invoke(cache);if(!profile.getName().equals("poposha"))return;if(!profile.getId().equals(POP))throw new AssertionError("Wrong displayed UUID");var button=mc.screen.children().stream().filter(x->x instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("Проверить доступ")).findFirst().orElseThrow(()->new AssertionError("Repair action missing"));((net.minecraft.client.gui.components.Button)button).onPress();stage=2;return;}
  if(stage==2){if(mc.screen==null||!mc.screen.getClass().getSimpleName().equals("UiConfirmDialog"))return;for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("Подтвердить")){b.onPress();stage=3;return;}throw new AssertionError("Confirmation action missing");}
  if(stage==3){UUID alias=UUID.fromString("63c33488-9c3b-497b-8069-6fd8361bc5d2"),linked=UUID.fromString("b1ec4c2c-28fa-4411-855c-bba9e5954b35"),unknown=UUID.fromString("99f85869-5f9f-48be-90bb-785b513dce91");if(entries.contains(alias))return;if(!entries.equals(Set.of(POP,linked,unknown)))throw new AssertionError("Wrong repaired set "+entries);System.out.println("ANTHUB_COMPAT_BRIDGE_OK: negotiated capability, unopened menu, offline add, server name, preview dialog, confirmed repair, unknown preserved");done=true;mc.stop();}
 }catch(Throwable error){done=true;System.out.println("ANTHUB_COMPAT_BRIDGE_FAILED stage="+stage);error.printStackTrace();mc.stop();}}
}
