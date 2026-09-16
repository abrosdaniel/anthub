package dev.abros.anthub.core.auth;
import dev.abros.anthub.core.TestDatabase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class LinkedAccountTest {
 @TempDir Path root;
 String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString(),official=UUID.randomUUID().toString();
 char[] pass(){return "server-password".toCharArray();}
 AuthStore store() throws Exception{return new AuthStore(TestDatabase.database(root));}
 @Test void officialLoginNeverCreatesAnAccount()throws Exception{var db=store();assertThrows(IllegalArgumentException.class,()->db.official("Alice",a,official,0));assertNull(db.account("Alice"));}
 @Test void linkPreservesIdentityAndBothLoginMethodsWork()throws Exception{
  var db=store();db.register("Alice",a,pass(),0);var before=db.remember("Alice","Laptop",1);var old=db.account("Alice");
  var linked=db.linkOfficial("Alice",a,official,pass(),2);
  assertEquals(a,linked.uuid());assertEquals("Alice",linked.name());assertEquals("local",linked.type());assertEquals(old.generation()+1,linked.generation());
  assertTrue(db.devices("Alice",3).isEmpty());assertFalse(db.validSessions(List.of(new AuthStore.SessionKey("Alice",old.generation(),"")),3).contains(new AuthStore.SessionKey("Alice",old.generation(),"")));
  assertEquals(a,db.login("ALICE",a,pass(),4).uuid());assertEquals(a,db.official("alice",a,official,5).uuid());
  assertThrows(IllegalArgumentException.class,()->db.register("alice",b,pass(),6));
 }
 @Test void bindingRequiresPasswordAndCannotOverwriteAnotherBinding()throws Exception{
  var db=store();db.register("Alice",a,pass(),0);assertThrows(IllegalArgumentException.class,()->db.linkOfficial("Alice",a,official,"wrong".toCharArray(),1));assertNull(db.account("Alice").official());
  db.linkOfficial("Alice",a,official,pass(),100000);
  assertThrows(IllegalArgumentException.class,()->db.linkOfficial("Alice",a,UUID.randomUUID().toString(),pass(),100001));
  assertEquals(official,db.account("Alice").official());
 }
 @Test void unlinkAndRecoveryRevokeOfficialAccess()throws Exception{
  var db=store();db.register("Alice",a,pass(),0);db.linkOfficial("Alice",a,official,pass(),1);
  assertThrows(IllegalArgumentException.class,()->db.unlinkOfficial("Alice",a,"wrong".toCharArray(),2));assertEquals(official,db.account("Alice").official());
  db.unlinkOfficial("Alice",a,pass(),100000);assertNull(db.account("Alice").official());assertEquals(a,db.login("Alice",a,pass(),100001).uuid());
  db.linkOfficial("Alice",a,official,pass(),100002);var invite=db.invite("console","Alice",100003);db.reset("Alice",a,invite,pass(),100004);
  assertNull(db.account("Alice").official());assertThrows(IllegalArgumentException.class,()->db.official("Alice",a,official,100005));
 }
 @Test void oneOfficialIdentityCannotBeLinkedTwiceConcurrently()throws Exception{
  var db=store();db.register("Alice",a,pass(),0);db.register("Bob",b,pass(),0);
  try(var workers=Executors.newFixedThreadPool(2)){
   var jobs=List.<Callable<Boolean>>of(()->link(db,"Alice",a),()->link(db,"Bob",b));int successes=0;for(var result:workers.invokeAll(jobs))if(result.get())successes++;
   assertEquals(1,successes);
  }
 }
 private boolean link(AuthStore db,String name,String id)throws Exception{try{db.linkOfficial(name,id,official,pass(),1);return true;}catch(IllegalArgumentException expected){return false;}}
 @Test void identityIndexPreservesStoredUuidAcrossRestartAndCase()throws Exception{
  var db=store();db.register("Alice",a,pass(),0);var index=new ServerIdentities(db.profiles());assertEquals(UUID.fromString(a),index.resolve("ALICE").uuid());assertEquals("Alice",index.resolve("alice").name());
 }
}
