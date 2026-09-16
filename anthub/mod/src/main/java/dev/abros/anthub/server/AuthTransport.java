package dev.abros.anthub.server;

import io.netty.handler.ssl.SslHandler;
import net.minecraft.network.Connection;
import javax.net.ssl.SSLContext;
import java.util.concurrent.CompletableFuture;

/** Protect the entire Minecraft byte stream with standard TLS, including post-login traffic. */
public final class AuthTransport {
    private AuthTransport() {}
    public static CompletableFuture<Void> install(Connection connection,SSLContext context,boolean client){
        var result=new CompletableFuture<Void>();var channel=connection.channel();
        channel.eventLoop().execute(()->{
            try{
                if(!channel.isActive()||channel.pipeline().get("anthub_auth_tls")!=null)throw new IllegalStateException("Unexpected TLS transport upgrade");
                var engine=context.createSSLEngine();engine.setUseClientMode(client);engine.setEnabledProtocols(new String[]{"TLSv1.3"});
                engine.setEnabledCipherSuites(new String[]{"TLS_AES_128_GCM_SHA256","TLS_AES_256_GCM_SHA384"});
                var handler=new SslHandler(engine);handler.setHandshakeTimeoutMillis(15000);
                handler.handshakeFuture().addListener(future->{if(future.isSuccess())result.complete(null);else{channel.close();result.completeExceptionally(future.cause());}});
                channel.pipeline().addFirst("anthub_auth_tls",handler);
            }catch(Exception ex){channel.close();result.completeExceptionally(ex);}
        });return result;
    }
}
