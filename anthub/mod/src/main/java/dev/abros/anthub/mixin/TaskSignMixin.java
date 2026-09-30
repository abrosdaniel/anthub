package dev.abros.anthub.mixin;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
@Mixin(SignBlockEntity.class)
public abstract class TaskSignMixin {
 @Unique private boolean anthub$accepted;
 @Inject(method="updateSignText",at=@At("HEAD")) private void before(Player player,boolean front,List<FilteredText> text,CallbackInfo ci){var sign=(SignBlockEntity)(Object)this;anthub$accepted=!sign.isWaxed()&&player.getUUID().equals(sign.getPlayerWhoMayEdit());}
 @Inject(method="updateSignText",at=@At("TAIL")) private void after(Player player,boolean front,List<FilteredText> text,CallbackInfo ci){if(anthub$accepted&&player instanceof ServerPlayer serverPlayer)dev.abros.anthub.server.ServerTaskStocks.signEdited(serverPlayer,(SignBlockEntity)(Object)this,front);anthub$accepted=false;}
}
