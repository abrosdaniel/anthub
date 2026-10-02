package dev.abros.anthub.mixin.compat;
import dev.abros.anthub.client.CompatibilityClient;
import dev.abros.anthub.compat.AccessDeniedBindings;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Pseudo @Mixin(targets="net.fw14.createAddons.accessDenied.screen.AccessControlScreen",remap=false)
public abstract class AccessDeniedScreenMixin {
 // Reflection keeps the optional PlayerEditBox type out of AntHub's class linkage.
 @Inject(method="submitToAdd",at=@At("HEAD"),cancellable=true,require=0)
 private void anthub$serverIdentity(CallbackInfo callback){
  if(!CompatibilityClient.active())return;callback.cancel();
  try{var target=AccessDeniedBindings.type("screen.AccessControlScreen");var input=target.getDeclaredField("playerUsernameBox");input.setAccessible(true);var box=(EditBox)input.get(this);var field=target.getDeclaredField("networkId");field.setAccessible(true);CompatibilityClient.add((UUID)field.get(this),box.getValue());box.setValue("");}
  catch(ReflectiveOperationException error){throw new IllegalStateException("AntHub Access Denied adapter contract changed",error);}
 }
}
