package dev.abros.anthub.server.compat;
import com.mojang.authlib.GameProfile;
import dev.abros.anthub.core.auth.AuthStore;
import dev.abros.anthub.server.AuthServer;
import net.minecraft.server.MinecraftServer;
import java.util.*;
/** Read-only public identities. Never creates accounts or guesses Mojang ownership. */
public final class ServerIdentityDirectory {
 private final MinecraftServer server;
 public ServerIdentityDirectory(MinecraftServer server){this.server=server;}
 public Optional<GameProfile> byName(String name){
  if(!AuthStore.validName(name))throw new IllegalArgumentException("Неверный ник игрока");
  var saved=AuthServer.knownIdentity(name);if(saved.isPresent())return saved.map(p->new GameProfile(p.uuid(),p.name()));
  var online=server.getPlayerList().getPlayerByName(name);if(online!=null)return Optional.of(online.getGameProfile());
  // With Auth enabled only registered identities are authoritative.
  if(AuthServer.enabled())return Optional.empty();
  var cache=server.getProfileCache();return cache==null?Optional.empty():cache.get(net.minecraft.core.UUIDUtil.createOfflineProfile(name).getId());
 }
 public Optional<GameProfile> byId(UUID id){var saved=AuthServer.knownIdentity(id);if(saved.isPresent())return saved.map(p->new GameProfile(p.uuid(),p.name()));var online=server.getPlayerList().getPlayer(id);if(online!=null)return Optional.of(online.getGameProfile());var cache=server.getProfileCache();return cache==null?Optional.empty():cache.get(id);}
 public Optional<UUID> verifiedAlias(UUID id){return AuthServer.verifiedAlias(id).map(AuthStore.Profile::uuid);}
}
