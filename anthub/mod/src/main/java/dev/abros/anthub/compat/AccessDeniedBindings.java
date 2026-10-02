package dev.abros.anthub.compat;

import net.neoforged.fml.ModList;
import java.lang.reflect.*;
import java.util.*;

/** Narrow reflective boundary: optional classes are never linked into AntHub's API. */
public final class AccessDeniedBindings {
 public static final String ID="create-access-denied", MOD="create_access_denied", VERSION="0.0.4+c6.0.10.mc1.21.1";
 public static final String BASE="net.fw14.createAddons.accessDenied.";
 private AccessDeniedBindings(){}
 public static boolean supported(){var info=ModList.get().getModContainerById(MOD);return info.isPresent()&&info.get().getModInfo().getVersion().toString().equals(VERSION);}
 public static String status(){if(!ModList.get().isLoaded(MOD))return "не установлен";return supported()?"активен":"неподдерживаемая версия (проверена "+VERSION+")";}
 public static Class<?> type(String name)throws ClassNotFoundException{return Class.forName(BASE+name);}
 public static Object manager()throws ReflectiveOperationException{return Class.forName("com.simibubi.create.Create").getField("LOGISTICS").get(null);}
 public static Object network(UUID id)throws ReflectiveOperationException{var value=((Map<?,?>)manager().getClass().getField("logisticsNetworks").get(manager())).get(id);if(value==null)throw new IllegalArgumentException("Логистическая сеть не найдена");return value;}
 public static Set<UUID> allowed(UUID id)throws ReflectiveOperationException{var raw=(Set<?>)type("extensions.LogisticNetworkExtensions").getMethod("accessDenied$getAllowedPlayers").invoke(network(id));var result=new HashSet<UUID>();for(var value:raw)result.add((UUID)value);return Set.copyOf(result);}
 public static boolean mayAdministrate(UUID id,net.minecraft.world.entity.player.Player player)throws ReflectiveOperationException{return (boolean)manager().getClass().getMethod("mayAdministrate",UUID.class,net.minecraft.world.entity.player.Player.class).invoke(manager(),id,player);}
 public static int limit()throws ReflectiveOperationException{return type("AccessDenied").getField("PLAYER_LIMIT").getInt(null);}
 public static void add(UUID network,UUID player)throws ReflectiveOperationException{type("extensions.LogisticNetworkExtensions").getMethod("accessDenied$addAllowedPlayer",UUID.class).invoke(network(network),player);}
 public static void replace(UUID network,Set<UUID> expected,Set<UUID> replacement)throws ReflectiveOperationException{
  if(!allowed(network).equals(expected))throw new IllegalArgumentException("Список доступа изменился. Повторите предпросмотр");
  if(replacement.size()>limit())throw new IllegalArgumentException("Превышен лимит игроков");
  // All operations run on the server thread; preserve unknown entries and existing limit.
  var target=network(network);var api=type("extensions.LogisticNetworkExtensions");
  for(var id:expected)if(!replacement.contains(id))api.getMethod("accessDenied$removeAllowedPlayer",UUID.class).invoke(target,id);
  for(var id:replacement)if(!expected.contains(id))api.getMethod("accessDenied$addAllowedPlayer",UUID.class).invoke(target,id);
 }
 public static void sync(UUID network,net.minecraft.server.level.ServerPlayer actor)throws ReflectiveOperationException{
  manager().getClass().getMethod("markDirty").invoke(manager());
  var packet=type("networking.S2CAllowedPlayersSyncPacket").getMethod("fromNetworkId",UUID.class).invoke(null,network);
  net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(actor,(net.minecraft.network.protocol.common.custom.CustomPacketPayload)packet);
 }
}
