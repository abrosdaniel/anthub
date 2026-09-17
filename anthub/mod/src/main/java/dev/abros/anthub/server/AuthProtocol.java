package dev.abros.anthub.server;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.function.BiConsumer;

public final class AuthProtocol {
    public static java.util.function.Consumer<IPayloadContext> upgrade=c->c.disconnect(net.minecraft.network.chat.Component.literal("Auth unavailable"));
    public static java.util.function.Consumer<IPayloadContext> ready=c->{};
    public static BiConsumer<String,IPayloadContext> hello=(p,c)->{};
    public static BiConsumer<byte[],IPayloadContext> client=(p,c)->{};
    public static BiConsumer<byte[],IPayloadContext> server=(p,c)->c.disconnect(net.minecraft.network.chat.Component.literal("Auth unavailable"));
    public record Hello(String fingerprint) implements CustomPacketPayload {
        public static final Type<Hello> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("anthub","auth_hello"));
        public static final StreamCodec<FriendlyByteBuf,Hello> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.fingerprint,64),b->new Hello(b.readUtf(64)));
        public Type<Hello> type(){return TYPE;}
    }
    public record Upgrade() implements CustomPacketPayload {
        public static final Type<Upgrade> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("anthub","auth_transport"));
        public static final StreamCodec<FriendlyByteBuf,Upgrade> CODEC=StreamCodec.unit(new Upgrade());
        public Type<Upgrade> type(){return TYPE;}
    }
    public record Ready() implements CustomPacketPayload {
        public static final Type<Ready> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("anthub","auth_transport_ready"));
        public static final StreamCodec<FriendlyByteBuf,Ready> CODEC=StreamCodec.unit(new Ready());
        public Type<Ready> type(){return TYPE;}
    }
    public record ToServer(byte[] bytes) implements CustomPacketPayload {
        public static final Type<ToServer> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("anthub","auth_request"));
        public static final StreamCodec<FriendlyByteBuf,ToServer> CODEC=StreamCodec.of((b,p)->b.writeByteArray(p.bytes),b->new ToServer(b.readByteArray(24576)));
        public Type<ToServer> type(){return TYPE;}
    }
    public record ToClient(byte[] bytes) implements CustomPacketPayload {
        public static final Type<ToClient> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("anthub","auth_response"));
        public static final StreamCodec<FriendlyByteBuf,ToClient> CODEC=StreamCodec.of((b,p)->b.writeByteArray(p.bytes),b->new ToClient(b.readByteArray(24576)));
        public Type<ToClient> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("auth-"+dev.abros.anthub.core.WireProtocols.version("auth")).optional();r.configurationToServer(Upgrade.TYPE,Upgrade.CODEC,(p,c)->upgrade.accept(c));r.configurationToClient(Ready.TYPE,Ready.CODEC,(p,c)->ready.accept(c));r.configurationToClient(Hello.TYPE,Hello.CODEC,(p,c)->hello.accept(p.fingerprint,c));r.commonToClient(ToClient.TYPE,ToClient.CODEC,(p,c)->client.accept(p.bytes,c));r.commonToServer(ToServer.TYPE,ToServer.CODEC,(p,c)->server.accept(p.bytes,c));}
}
