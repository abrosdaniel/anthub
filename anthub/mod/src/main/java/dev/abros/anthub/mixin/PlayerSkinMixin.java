package dev.abros.anthub.mixin;
import dev.abros.anthub.client.SkinClient;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerInfo.class)
public abstract class PlayerSkinMixin {
 @Inject(method="getSkin",at=@At("RETURN"),cancellable=true)
 private void anthubSkin(CallbackInfoReturnable<PlayerSkin> callback){callback.setReturnValue(SkinClient.skin(((PlayerInfo)(Object)this).getProfile().getId(),callback.getReturnValue()));}
}
