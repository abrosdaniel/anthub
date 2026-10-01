package dev.abros.anthub.mixin;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Field access only; vanilla rendering and input are not modified. */
@Mixin(MultiLineEditBox.class)
public interface MultiLineFieldAccessor { @Accessor("textField") MultilineTextField anthub$textField(); }
