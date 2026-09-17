package dev.abros.anthub.core.auth;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ServerIdentitiesTest {
 @Test void newOfflineNameKeepsMinecraftUuid(){var index=new ServerIdentities(List.of());assertEquals(UUID.fromString("f462eb29-aa29-3588-8da2-a2565774ca02"),index.resolve("ADMIN").uuid());assertNotEquals(index.resolve("ADMIN").uuid(),index.resolve("admin").uuid());}
 @Test void registrationPinsOriginalUuidAcrossCase(){var index=new ServerIdentities(List.of());var original=UUID.randomUUID();assertEquals(original,index.resolve("ADMIN",original,null).uuid());index.remember(index.resolve("ADMIN",original,null));assertEquals(original,index.resolve("admin",UUID.randomUUID(),null).uuid());}
 @Test void storedProfileWinsIncludingSpelling(){var id=UUID.randomUUID();var index=new ServerIdentities(List.of(new AuthStore.Profile("Admin",id)));assertEquals(new AuthStore.Profile("Admin",id),index.resolve("ADMIN"));}
 @Test void lookupNameValidationIsNullSafeAndKeepsAuthenticationStrict(){
  assertFalse(AuthStore.validName(null));
  assertThrows(IllegalArgumentException.class,()->AuthStore.name(null));
  for(String name:List.of("","a/b","display owner","x".repeat(17),"§cAdmin")){
   assertFalse(AuthStore.validName(name));
   assertThrows(IllegalArgumentException.class,()->AuthStore.name(name));
  }
  for(String name:List.of("ABROSxd","ADMIN","a","Player_123","x".repeat(16)))assertTrue(AuthStore.validName(name));
 }
 @Test void invalidNamesAreRejected(){var index=new ServerIdentities(List.of());for(String s:List.of("","a/b","x".repeat(17)))assertThrows(IllegalArgumentException.class,()->index.resolve(s));}
 @Test void linkedLauncherRoutesAfterRenameAndUnlinkRemovesRoute(){var id=UUID.randomUUID();var official=UUID.randomUUID();var profile=new AuthStore.Profile("ServerName",id,official);var index=new ServerIdentities(List.of(profile));assertEquals(profile,index.resolve("NewMinecraftName",official));index.remember(new AuthStore.Profile("ServerName",id));assertNotEquals(id,index.resolve("NewMinecraftName",official).uuid());}
}
