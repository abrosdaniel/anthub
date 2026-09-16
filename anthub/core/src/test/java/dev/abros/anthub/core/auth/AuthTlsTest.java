package dev.abros.anthub.core.auth;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AuthTlsTest {
 @TempDir Path root;
 @Test void tlsHandshakeFramingAndEncryptedPassword()throws Exception{
  var identity=AuthTls.identity(root);var clientWire=new ArrayDeque<byte[]>();var serverWire=new ArrayDeque<byte[]>();var incoming=new ArrayList<String>();var replies=new ArrayList<String>();
  var client=new AuthTls.Tunnel(AuthTls.client(identity.fingerprint()),true,clientWire::add,replies::add);var server=new AuthTls.Tunnel(identity.context(),false,serverWire::add,incoming::add);
  client.receive(new byte[0]);pump(client,server,clientWire,serverWire);assertTrue(client.ready());assertTrue(server.ready());
  String secret="{\"password\":\"highly secret password\"}";client.send(secret);byte[] wire=clientWire.peek();assertFalse(new String(wire,java.nio.charset.StandardCharsets.ISO_8859_1).contains("highly secret password"));pump(client,server,clientWire,serverWire);assertEquals(List.of(secret),incoming);
  server.send("{\"status\":\"ok\"}");pump(client,server,clientWire,serverWire);assertEquals(1,replies.size());
  assertThrows(Exception.class,()->server.receive(wire));
 }
 @Test void differentPinnedIdentityRejected()throws Exception{var identity=AuthTls.identity(root);var c=new ArrayDeque<byte[]>();var s=new ArrayDeque<byte[]>();var client=new AuthTls.Tunnel(AuthTls.client("0".repeat(64)),true,c::add,x->fail());var server=new AuthTls.Tunnel(identity.context(),false,s::add,x->fail());client.receive(new byte[0]);assertThrows(Exception.class,()->pump(client,server,c,s));}
 @Test void identityPersistsAndCorruptionNeverRegenerates()throws Exception{String first=AuthTls.identity(root).fingerprint();assertEquals(first,AuthTls.identity(root).fingerprint());Files.writeString(root.resolve("auth-identity.p12"),"broken");assertThrows(Exception.class,()->AuthTls.identity(root));assertEquals("broken",Files.readString(root.resolve("auth-identity.p12")));}
 @Test void cannotSendBeforeHandshake()throws Exception{var tunnel=new AuthTls.Tunnel(AuthTls.client("0".repeat(64)),true,b->{},s->{});assertThrows(Exception.class,()->tunnel.send("{}"));}
 private static void pump(AuthTls.Tunnel c,AuthTls.Tunnel s,Queue<byte[]> cq,Queue<byte[]> sq)throws Exception{for(int i=0;i<100&&(!cq.isEmpty()||!sq.isEmpty());i++){while(!cq.isEmpty())s.receive(cq.remove());while(!sq.isEmpty())c.receive(sq.remove());}assertTrue(cq.isEmpty()&&sq.isEmpty());}
}
