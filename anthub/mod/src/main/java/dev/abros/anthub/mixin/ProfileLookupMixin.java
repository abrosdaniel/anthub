package dev.abros.anthub.mixin;
import com.mojang.authlib.GameProfile;
import dev.abros.anthub.server.AuthServer;
import net.minecraft.server.players.GameProfileCache;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Administrative name lookups use the same identity as incoming players. */
@Mixin(GameProfileCache.class)
abstract class ProfileLookupMixin {
 @Inject(method="get(Ljava/lang/String;)Ljava/util/Optional;",at=@At("HEAD"),cancellable=true)
 private void anthub$lookup(String name,CallbackInfoReturnable<Optional<GameProfile>> cir){
  var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
  if(AuthServer.enabled()&&server!=null&&!server.usesAuthentication()&&name.matches("[A-Za-z0-9_]{1,16}"))cir.setReturnValue(Optional.of(AuthServer.profile(net.minecraft.core.UUIDUtil.createOfflineProfile(name))));
 }
 @Inject(method="get(Ljava/lang/String;)Ljava/util/Optional;",at=@At("RETURN"),cancellable=true)
 private void anthub$verifiedLookup(String name,CallbackInfoReturnable<Optional<GameProfile>> cir){
  if(AuthServer.enabled())cir.setReturnValue(cir.getReturnValue().map(AuthServer::profile));
 }
}
