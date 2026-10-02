package dev.abros.anthub.client;
import dev.abros.anthub.core.NativeLayout;
import net.minecraft.client.gui.components.*;
import java.util.function.Consumer;
/** Application footer: semantic commands own their dimensions; pages supply only behavior. */
record UiPageFooter(NativeLayout.Box start,NativeLayout.Box status,NativeLayout.Box end) {
 static UiPageFooter fit(NativeLayout.Box area){var slots=NativeLayout.row(area,UiActions.GAP,NativeLayout.Track.fixed(UiActions.COMMAND_WIDTH),NativeLayout.Track.flex(1),NativeLayout.Track.fixed(UiActions.COMMAND_WIDTH));return new UiPageFooter(slots.get(0),slots.get(1),slots.get(2));}
 static UiPageFooter workspace(int width,int height){return fit(UiWorkspace.fit(width,height).footer());}
 Button start(UiActions.Command command,Consumer<AbstractWidget> add,Runnable run){return UiActions.command(command,start,add,run);}
 Button end(UiActions.Command command,Consumer<AbstractWidget> add,Runnable run){return UiActions.command(command,end,add,run);}
}
