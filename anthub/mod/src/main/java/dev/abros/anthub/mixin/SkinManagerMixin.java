package dev.abros.anthub.mixin;

import com.mojang.authlib.GameProfile;
import dev.abros.anthub.client.SkinClient;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Covers profile-based renderers (including Sable ragdolls), not only live PlayerInfo. */
@Mixin(SkinManager.class)
public abstract class SkinManagerMixin {
 @Inject(method="getInsecureSkin",at=@At("RETURN"),cancellable=true)
 private void anthubSkin(GameProfile profile,CallbackInfoReturnable<PlayerSkin> ci){
  ci.setReturnValue(SkinClient.skin(profile.getId(),ci.getReturnValue()));
 }
 @Inject(method="getOrLoad",at=@At("RETURN"),cancellable=true)
 private void anthubFuture(GameProfile profile,CallbackInfoReturnable<CompletableFuture<PlayerSkin>> ci){
  ci.setReturnValue(SkinClient.resolveFuture(profile.getId(),ci.getReturnValue()));
 }
 @Inject(method="lookupInsecure",at=@At("HEAD"),cancellable=true)
 private void anthubSupplier(GameProfile profile,CallbackInfoReturnable<Supplier<PlayerSkin>> ci){
  // Re-resolve every render: a supplier may outlive a skin switch, deletion or reconnect.
  ci.setReturnValue(()->SkinClient.skin(profile.getId(),SkinClient.vanillaSkin(profile)));
 }
}
