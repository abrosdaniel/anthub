package dev.abros.anthub.mixin;
import dev.abros.anthub.client.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Screen.class)
abstract class MenuScreenMixin {
 @Inject(method="renderBackground",at=@At("RETURN"))
 private void anthub$shell(GuiGraphics g,int x,int y,float delta,CallbackInfo ci){UiTheme.shell((Screen)(Object)this,g);}
 @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true)
 private void anthub$keys(int key,int scan,int mods,CallbackInfoReturnable<Boolean> ci){if(UiTheme.keyboard((Screen)(Object)this,key))ci.setReturnValue(true);}
}
