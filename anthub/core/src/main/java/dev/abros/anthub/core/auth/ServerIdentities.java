package dev.abros.anthub.core.auth;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Public profile routing only; claimed launcher UUIDs never prove authentication. */
public final class ServerIdentities {
 private final Map<String,AuthStore.Profile> profiles=new HashMap<>();
 private final Map<UUID,AuthStore.Profile> ids=new HashMap<>();
 private final Map<UUID,AuthStore.Profile> linked=new HashMap<>();
 public ServerIdentities(Collection<AuthStore.Profile> initial){for(var profile:initial)remember(profile);}
 public synchronized void remember(AuthStore.Profile profile){
  var previous=ids.put(profile.uuid(),profile);
  if(previous!=null){profiles.remove(previous.name().toLowerCase(Locale.ROOT),previous);if(previous.official()!=null)linked.remove(previous.official(),previous);}
  profiles.put(AuthStore.name(profile.name()).toLowerCase(Locale.ROOT),profile);
  if(profile.official()!=null)linked.put(profile.official(),profile);
 }
 public synchronized Optional<AuthStore.Profile> known(String name){return Optional.ofNullable(profiles.get(AuthStore.name(name).toLowerCase(Locale.ROOT)));}
 public synchronized Optional<AuthStore.Profile> known(UUID uuid){return Optional.ofNullable(ids.get(uuid));}
 public synchronized Optional<AuthStore.Profile> verifiedAlias(UUID uuid){return Optional.ofNullable(linked.get(uuid));}
 public synchronized AuthStore.Profile resolve(String name){String key=AuthStore.name(name).toLowerCase(Locale.ROOT);var saved=profiles.get(key);return saved!=null?saved:new AuthStore.Profile(name,UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8)));}
 public synchronized AuthStore.Profile resolve(String name,UUID originalUuid,UUID claimedOfficial){var profile=linked.get(claimedOfficial);if(profile!=null)return profile;var saved=profiles.get(AuthStore.name(name).toLowerCase(Locale.ROOT));return saved!=null?saved:new AuthStore.Profile(name,Objects.requireNonNull(originalUuid));}
 public synchronized AuthStore.Profile resolve(String name,UUID claimedOfficial){var profile=linked.get(claimedOfficial);return profile!=null?profile:resolve(name);}
}
