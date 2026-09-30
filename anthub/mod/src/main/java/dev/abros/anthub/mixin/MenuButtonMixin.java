package dev.abros.anthub.mixin;
import dev.abros.anthub.client.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractButton.class)
abstract class MenuButtonMixin {
 @Inject(method="renderWidget",at=@At("HEAD"),cancellable=true)
 private void anthub$button(GuiGraphics g,int x,int y,float delta,CallbackInfo ci){if(UiTheme.stylesButtons(Minecraft.getInstance().screen,(AbstractButton)(Object)this)){UiTheme.button((AbstractButton)(Object)this,g);ci.cancel();}}
}
