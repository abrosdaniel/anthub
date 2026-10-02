package dev.abros.anthub.compattests;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.abros.anthub.compat.AccessDeniedBindings;
import dev.abros.anthub.core.auth.*;
import dev.abros.anthub.server.AuthServer;
import dev.abros.anthub.server.compat.ServerIdentityDirectory;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;
@EventBusSubscriber(modid="anthub_compat_tests",value=Dist.DEDICATED_SERVER)
public final class CompatibilityServerHarness {
 @SubscribeEvent public static void start(ServerStartedEvent event){String mode=System.getenv("ANTHUB_COMPAT_SMOKE");if(mode==null)return;var server=event.getServer();java.lang.reflect.Field indexField=null;Object original=null;UUID network=null;try{
  if(mode.equals("bridge")){
   var id=UUID.fromString("b5d70e3e-704c-4cc5-9829-cfa7c55fcff3");var type=Class.forName("com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork");var n=type.getConstructor(UUID.class).newInstance(id);type.getField("owner").set(n,net.minecraft.core.UUIDUtil.createOfflineProfile("Tester").getId());type.getField("locked").setBoolean(n,true);((Map<UUID,Object>)AccessDeniedBindings.manager().getClass().getField("logisticsNetworks").get(AccessDeniedBindings.manager())).put(id,n);
   UUID pop=UUID.fromString("1edbd7a9-088f-34ae-8786-d56a6299d1df"),linked=UUID.fromString("b1ec4c2c-28fa-4411-855c-bba9e5954b35"),alias=UUID.fromString("63c33488-9c3b-497b-8069-6fd8361bc5d2"),unknown=UUID.fromString("99f85869-5f9f-48be-90bb-785b513dce91");var index=AuthServer.class.getDeclaredField("identities");index.setAccessible(true);index.set(null,new ServerIdentities(List.of(new AuthStore.Profile("poposha",pop),new AuthStore.Profile("Linked",linked,alias))));AccessDeniedBindings.add(id,alias);AccessDeniedBindings.add(id,unknown);System.out.println("ANTHUB_COMPAT_BRIDGE_READY");return;
  }
  if(mode.equals("absent")){if(AccessDeniedBindings.supported())throw new AssertionError("Unexpected optional mod");System.out.println("ANTHUB_COMPAT_SERVER_OK absent");return;}
  if(!AccessDeniedBindings.supported())throw new AssertionError(AccessDeniedBindings.status());
  var owner=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"Owner"));var other=FakePlayerFactory.get(server.overworld(),new GameProfile(UUID.randomUUID(),"Other"));
  UUID pop=UUID.fromString("1edbd7a9-088f-34ae-8786-d56a6299d1df"),official=UUID.fromString("e95fa0b8-2590-424a-b712-6f216a65b659"),linked=UUID.randomUUID(),alias=UUID.randomUUID(),unknown=UUID.randomUUID();
  var index=new ServerIdentities(List.of(new AuthStore.Profile("poposha",pop),new AuthStore.Profile("Linked",linked,alias)));indexField=AuthServer.class.getDeclaredField("identities");indexField.setAccessible(true);original=indexField.get(null);indexField.set(null,index);
  var directory=new ServerIdentityDirectory(server);var type=Class.forName("com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork");network=UUID.randomUUID();var instance=type.getConstructor(UUID.class).newInstance(network);type.getField("owner").set(instance,owner.getUUID());type.getField("locked").setBoolean(instance,true);
  var networks=(Map<UUID,Object>)AccessDeniedBindings.manager().getClass().getField("logisticsNetworks").get(AccessDeniedBindings.manager());networks.put(network,instance);
  var adapterType=Class.forName("dev.abros.anthub.server.compat.CreateAccessDeniedAdapter");var constructor=adapterType.getDeclaredConstructor();constructor.setAccessible(true);var adapter=constructor.newInstance();var execute=adapterType.getDeclaredMethod("execute",ServerPlayer.class,JsonObject.class,ServerIdentityDirectory.class);execute.setAccessible(true);
  var add=query("add",network);add.addProperty("name","POPOSHA");execute.invoke(adapter,owner,add,directory);check(AccessDeniedBindings.allowed(network).equals(Set.of(pop)),"Offline name must use server UUID");check(!AccessDeniedBindings.allowed(network).contains(official),"Mojang UUID leaked");execute.invoke(adapter,owner,add,directory);check(AccessDeniedBindings.allowed(network).size()==1,"Duplicate add");
  try{execute.invoke(adapter,other,add,directory);throw new AssertionError("Unauthorised add accepted");}catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof IllegalArgumentException,"Unexpected denied failure");}
  AccessDeniedBindings.add(network,alias);AccessDeniedBindings.add(network,unknown);var preview=(JsonObject)execute.invoke(adapter,owner,query("preview",network),directory);check(preview.getAsJsonArray("replacements").size()==1,"Verified-only preview");var apply=query("apply",network);apply.add("token",preview.get("token"));execute.invoke(adapter,owner,apply,directory);check(AccessDeniedBindings.allowed(network).equals(Set.of(pop,linked,unknown)),"Repair altered unknown or server UUID");
  var saved=type.getMethod("write",net.minecraft.core.HolderLookup.Provider.class).invoke(instance,server.registryAccess());var restored=type.getMethod("read",net.minecraft.nbt.CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class).invoke(null,saved,server.registryAccess());check(((Set<?>)AccessDeniedBindings.type("extensions.LogisticNetworkExtensions").getMethod("accessDenied$getAllowedPlayers").invoke(restored)).equals(Set.of(pop,linked,unknown)),"Persistence roundtrip lost permissions");
  AccessDeniedBindings.add(network,alias);preview=(JsonObject)execute.invoke(adapter,owner,query("preview",network),directory);apply=query("apply",network);apply.add("token",preview.get("token"));AccessDeniedBindings.add(network,UUID.randomUUID());try{execute.invoke(adapter,owner,apply,directory);throw new AssertionError("Stale preview accepted");}catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof IllegalArgumentException,"Wrong stale failure");}
  index.remember(new AuthStore.Profile("Renamed",pop));add=query("add",network);add.addProperty("name","Renamed");execute.invoke(adapter,owner,add,directory);check(directory.byName("poposha").isEmpty(),"Stale name alias");check(directory.byName("Renamed").orElseThrow().getId().equals(pop),"Rename changed UUID");
  AccessDeniedBindings.type("extensions.LogisticNetworkExtensions").getMethod("accessDenied$removeAllowedPlayer",UUID.class).invoke(instance,pop);check(!AccessDeniedBindings.allowed(network).contains(pop),"Removal failed");
  System.out.println("ANTHUB_COMPAT_SERVER_OK present: offline lookup, duplicate, owner rights, verified repair, unknown preservation, stale preview, persistence, rename, removal");
 }catch(Throwable error){System.out.println("ANTHUB_COMPAT_SERVER_FAILED "+mode);error.printStackTrace();}finally{try{if(indexField!=null)indexField.set(null,original);if(network!=null)((Map<?,?>)AccessDeniedBindings.manager().getClass().getField("logisticsNetworks").get(AccessDeniedBindings.manager())).remove(network);}catch(Exception ignored){}if(!mode.equals("bridge"))server.halt(false);}}
 @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event){if("bridge".equals(System.getenv("ANTHUB_COMPAT_SMOKE"))&&event.getEntity() instanceof ServerPlayer p)try{AccessDeniedBindings.sync(UUID.fromString("b5d70e3e-704c-4cc5-9829-cfa7c55fcff3"),p);}catch(Exception error){throw new IllegalStateException(error);}}
 private static JsonObject query(String op,UUID network){var j=new JsonObject();j.addProperty("op",op);j.addProperty("network",network.toString());return j;}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
