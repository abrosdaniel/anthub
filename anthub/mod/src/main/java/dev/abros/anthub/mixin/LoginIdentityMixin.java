package dev.abros.anthub.mixin;
import com.mojang.authlib.GameProfile;
import dev.abros.anthub.server.AuthServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Resolve before whitelist, duplicate-session checks and login success. */
@Mixin(ServerLoginPacketListenerImpl.class)
abstract class LoginIdentityMixin {
 @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private net.minecraft.server.MinecraftServer server;
 @org.spongepowered.asm.mixin.Shadow public abstract void disconnect(net.minecraft.network.chat.Component reason);
 @Inject(method="verifyLoginAndFinishConnectionSetup",at=@At("HEAD"),cancellable=true)
 private void anthub$protectSession(GameProfile profile,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
  if(AuthServer.enabled()&&server.getPlayerList().getPlayer(profile.getId())!=null){disconnect(net.minecraft.network.chat.Component.literal("Этот аккаунт уже находится на сервере"));ci.cancel();}
 }

 @org.spongepowered.asm.mixin.Unique private java.util.UUID anthub$claimedOfficial;
 @Inject(method="handleHello",at=@At("HEAD"))
 private void anthub$launcher(net.minecraft.network.protocol.login.ServerboundHelloPacket packet,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){anthub$claimedOfficial=packet.profileId();}
 @ModifyVariable(method="startClientVerification",at=@At("HEAD"),argsOnly=true)
 private GameProfile anthub$identity(GameProfile profile){return AuthServer.profile(profile,server.usesAuthentication()?profile.getId():anthub$claimedOfficial);}
}
