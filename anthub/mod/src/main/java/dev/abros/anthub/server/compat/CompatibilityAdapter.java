package dev.abros.anthub.server.compat;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
/** Each optional integration owns its supported versions, operations and permissions. */
public interface CompatibilityAdapter {
 String id();
 String status();
 JsonObject execute(ServerPlayer actor,JsonObject request,ServerIdentityDirectory identities)throws Exception;
 default void clear(){}
}
