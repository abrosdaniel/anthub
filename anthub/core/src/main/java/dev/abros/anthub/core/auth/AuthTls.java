package dev.abros.anthub.core.auth;

import javax.net.ssl.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.function.Consumer;
import java.io.*;
import java.math.BigInteger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** TLS 1.3 records carried by bounded Minecraft payloads. No application cryptography. */
public final class AuthTls {
    private AuthTls() {}
    public record Identity(SSLContext context,String fingerprint) {}
    public static Identity identity(Path directory)throws Exception {
        Files.createDirectories(directory);Path file=directory.resolve("auth-identity.p12");
        if(Files.isSymbolicLink(directory)||Files.isSymbolicLink(file))throw new IOException("Unsafe identity path");
        var ks=KeyStore.getInstance("PKCS12");char[] password=new char[0];
        if(Files.exists(file)){try(var in=Files.newInputStream(file)){ks.load(in,password);}}
        else {
            ks.load(null,password);var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);var pair=generator.generateKeyPair();
            long now=System.currentTimeMillis();var subject=new X500Name("CN=AntHub Server");
            var builder=new JcaX509v3CertificateBuilder(subject,new BigInteger(128,new SecureRandom()),new Date(now-86400000),new Date(now+10L*365*86400000),subject,pair.getPublic());
            var certificate=new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
            ks.setKeyEntry("server",pair.getPrivate(),password,new java.security.cert.Certificate[]{certificate});
            Path tmp=Files.createTempFile(directory,"auth-identity-",".tmp");try{privateFile(tmp);try(var out=Files.newOutputStream(tmp)){ks.store(out,password);}Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(tmp);}
        }
        privateFile(file);var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,password);
        SSLContext context=SSLContext.getInstance("TLSv1.3");context.init(km.getKeyManagers(),null,new SecureRandom());
        return new Identity(context,AuthSecrets.digest(ks.getCertificate("server").getEncoded()));
    }
    public static void privateFile(Path path)throws IOException {try{Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored){}}
    public static SSLContext client(String fingerprint)throws Exception {
        if(!fingerprint.matches("[a-f0-9]{64}"))throw new CertificateException("Invalid server fingerprint");
        X509TrustManager trust=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String type)throws CertificateException{throw new CertificateException("Client certificates unsupported");}
            public void checkServerTrusted(X509Certificate[] chain,String type)throws CertificateException{
                if(chain.length!=1||!fingerprint.equals(AuthSecrets.digest(chain[0].getEncoded())))throw new CertificateException("Server identity changed");chain[0].checkValidity();
            }
        };
        var context=SSLContext.getInstance("TLSv1.3");context.init(null,new TrustManager[]{trust},new SecureRandom());return context;
    }
    public static final class Tunnel {
        private final SSLEngine engine;
        private final Consumer<byte[]> outbound;
        private final Consumer<String> inbound;
        private final ByteBuffer network=ByteBuffer.allocate(65536),plain=ByteBuffer.allocate(65536);
        private boolean ready,closed;
        public Tunnel(SSLContext context,boolean client,Consumer<byte[]> outbound,Consumer<String> inbound)throws SSLException {
            this.outbound=outbound;this.inbound=inbound;engine=context.createSSLEngine();engine.setUseClientMode(client);engine.setEnabledProtocols(new String[]{"TLSv1.3"});engine.setEnabledCipherSuites(new String[]{"TLS_AES_128_GCM_SHA256","TLS_AES_256_GCM_SHA384"});engine.beginHandshake();
        }
        public synchronized boolean ready(){return ready&&!closed;}
        public synchronized void receive(byte[] data)throws Exception {
            if(closed||data.length>24576||data.length>network.remaining())throw new SSLException("Invalid TLS input");network.put(data);pump();
        }
        private void tasks(){Runnable task;while((task=engine.getDelegatedTask())!=null)task.run();}
        private void pump()throws Exception {
            for(int attempts=0;attempts<128;attempts++){
                var hs=engine.getHandshakeStatus();
                if(hs==SSLEngineResult.HandshakeStatus.NEED_TASK){tasks();continue;}
                if(hs==SSLEngineResult.HandshakeStatus.NEED_WRAP){wrap(ByteBuffer.allocate(0));continue;}
                if(hs==SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)ready=true;
                if(network.position()==0)break;
                network.flip();int before=network.remaining();var r=engine.unwrap(network,plain);network.compact();check(r);
                if(r.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.FINISHED)ready=true;
                if(r.getStatus()==SSLEngineResult.Status.BUFFER_UNDERFLOW)break;
                if(before==network.position()&&r.bytesProduced()==0&&r.getHandshakeStatus()!=SSLEngineResult.HandshakeStatus.NEED_TASK)break;
            }
            // Process only complete length-prefixed application messages after authentication of TLS peer.
            plain.flip();var messages=new ArrayList<String>();while(ready&&plain.remaining()>=4){plain.mark();int length=plain.getInt();if(length<2||length>16384)throw new SSLException("Invalid auth message size");if(plain.remaining()<length){plain.reset();break;}byte[] bytes=new byte[length];plain.get(bytes);messages.add(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));}plain.compact();
            for(String message:messages)inbound.accept(message);
        }
        public synchronized void send(String message)throws Exception {
            if(!ready())throw new SSLException("TLS not established");byte[] bytes=message.getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>16384)throw new SSLException("Auth response too large");
            var app=ByteBuffer.allocate(bytes.length+4).putInt(bytes.length).put(bytes);app.flip();
            for(int attempts=0;app.hasRemaining();attempts++){
                if(attempts>=128)throw new SSLException("TLS output made no progress");
                if(engine.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_TASK){tasks();continue;}
                int before=app.remaining();wrap(app);
                if(app.remaining()==before&&engine.getHandshakeStatus()!=SSLEngineResult.HandshakeStatus.NEED_TASK&&engine.getHandshakeStatus()!=SSLEngineResult.HandshakeStatus.NEED_WRAP)throw new SSLException("TLS output needs peer data");
            }
        }
        private void wrap(ByteBuffer app)throws Exception {
            ByteBuffer out=ByteBuffer.allocate(24576);var r=engine.wrap(app,out);check(r);out.flip();if(out.hasRemaining()){byte[] bytes=new byte[out.remaining()];out.get(bytes);outbound.accept(bytes);}
        }
        private void check(SSLEngineResult r)throws SSLException {
            if(r.getStatus()==SSLEngineResult.Status.CLOSED||r.getStatus()==SSLEngineResult.Status.BUFFER_OVERFLOW){closed=true;throw new SSLException("TLS connection closed");}
        }
        public synchronized void close(){closed=true;engine.closeOutbound();Arrays.fill(network.array(),(byte)0);Arrays.fill(plain.array(),(byte)0);}
    }
}
