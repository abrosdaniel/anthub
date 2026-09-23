package dev.abros.anthub.network;
import dev.abros.anthub.core.Json;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public final class SkinWire {
 public static java.util.function.BiConsumer<JsonObject,IPayloadContext> server=(j,c)->{};
 public static java.util.function.Consumer<JsonObject> client=j->{};
 public record Request(String json) implements CustomPacketPayload{
  public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("anthub","skin_request"));
  public static final StreamCodec<FriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,8192),b->new Request(b.readUtf(8192)));
  public Type<Request> type(){return TYPE;}
 }
 public record Response(String json) implements CustomPacketPayload{
  public static final Type<Response> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("anthub","skin_response"));
  public static final StreamCodec<FriendlyByteBuf,Response> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,32767),b->new Response(b.readUtf(32767)));
  public Type<Response> type(){return TYPE;}
 }
 public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("3").optional();r.playToServer(Request.TYPE,Request.CODEC,(p,c)->c.enqueueWork(()->{try{server.accept(Json.parse(p.json),c);}catch(Exception ignored){}}));r.playToClient(Response.TYPE,Response.CODEC,(p,c)->c.enqueueWork(()->{try{client.accept(Json.parse(p.json));}catch(Exception ignored){}}));}
}
