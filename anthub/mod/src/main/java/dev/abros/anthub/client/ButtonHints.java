package dev.abros.anthub.client;
import dev.abros.anthub.core.UiHelp;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
@EventBusSubscriber(modid="anthub",value=Dist.CLIENT)
final class ButtonHints {
 @SubscribeEvent public static void initialized(ScreenEvent.Init.Post event){if(event.getScreen().getClass().getPackageName().equals(ButtonHints.class.getPackageName()))apply(event.getScreen());}
 static void apply(Screen screen){for(var child:screen.children())if(child instanceof Button button){String text=UiHelp.text(button.getMessage().getString());if(!text.isEmpty()){button.setTooltip(Tooltip.create(Component.literal(text)));button.setTooltipDelay(java.time.Duration.ofMillis(400));}}}
}
