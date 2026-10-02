package dev.abros.anthub.mixin.compat;
import dev.abros.anthub.client.CompatibilityClient;
import dev.abros.anthub.compat.AccessDeniedBindings;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

@Pseudo @Mixin(targets="net.fw14.createAddons.accessDenied.screen.AccessControlScreen$ProfileCache",remap=false)
public abstract class AccessDeniedProfileMixin {
 @Inject(method="get",at=@At("HEAD"),cancellable=true,require=0)
 private static void anthub$publicProfile(UUID id,Minecraft minecraft,CallbackInfoReturnable<Object> callback){
  if(!CompatibilityClient.active())return;
  var profile=CompatibilityClient.profile(id);var skin=minecraft.getSkinManager().getInsecureSkin(profile);
  try{callback.setReturnValue(AccessDeniedBindings.type("screen.AccessControlScreen$ProfileCache").getConstructor(com.mojang.authlib.GameProfile.class,net.minecraft.resources.ResourceLocation.class).newInstance(profile,skin.texture()));}
  catch(ReflectiveOperationException error){throw new IllegalStateException("AntHub Access Denied profile contract changed",error);}
 }
}
