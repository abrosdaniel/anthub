package dev.abros.anthub.core.auth;
import dev.abros.anthub.core.TestDatabase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class PasswordPolicyTest {
 @TempDir Path root;
 @Test void defaultAllowsSixButRejectsFive() throws Exception {
  var store=new AuthStore(TestDatabase.database(root));String id=UUID.randomUUID().toString();
  assertEquals(6,store.minimumPasswordLength());
  assertThrows(IllegalArgumentException.class,()->store.register("Alice",id,"12345".toCharArray(),0));
  store.register("Alice",id,"123456".toCharArray(),0);
  assertNotNull(store.login("Alice",id,"123456".toCharArray(),1));
 }
 @Test void increasedMinimumAppliesToEveryNewPasswordButNotExistingLogin() throws Exception {
  var db=TestDatabase.database(root);String id=UUID.randomUUID().toString();
  new AuthStore(db).register("Alice",id,"123456".toCharArray(),0);
  var store=new AuthStore(db,10);
  assertNotNull(store.login("Alice",id,"123456".toCharArray(),1));
  assertThrows(IllegalArgumentException.class,()->store.register("Bob",UUID.randomUUID().toString(),"123456789".toCharArray(),2));
  assertThrows(IllegalArgumentException.class,()->store.change("Alice",id,"123456".toCharArray(),"123456789".toCharArray(),3));
  String invite=store.invite("console","Alice",4);
  assertThrows(IllegalArgumentException.class,()->store.reset("Alice",id,invite,"123456789".toCharArray(),5));
  store.reset("Alice",id,invite,"1234567890".toCharArray(),6);
  store.change("Alice",id,"1234567890".toCharArray(),"abcdefghij".toCharArray(),7);
  assertNotNull(store.login("Alice",id,"abcdefghij".toCharArray(),8));
 }
 @Test void boundsAndMaximumAreEnforced() {
  assertThrows(IllegalArgumentException.class,()->new AuthStore(null,5));
  assertThrows(IllegalArgumentException.class,()->new AuthStore(null,129));
  assertThrows(IllegalArgumentException.class,()->AuthSecrets.password("x".repeat(129).toCharArray(),6));
  String hash=AuthSecrets.password("x".repeat(128).toCharArray(),128);
  assertTrue(AuthSecrets.verify("x".repeat(128).toCharArray(),hash));
 }
}
