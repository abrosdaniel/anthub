package dev.abros.anthub.client;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
@EventBusSubscriber(modid="anthub",value=Dist.CLIENT)
public final class BugsUiHarness {
 private static int step=-1;private static long at;private static CoreVersionsPopup popup;
 @SubscribeEvent public static void tick(ScreenEvent.Render.Post e){String output=System.getenv("ANTHUB_BUGS_UI");if(output==null||step>=18)return;var mc=Minecraft.getInstance();if(step<0){if(!(mc.screen instanceof TitleScreen))return;step=0;at=System.currentTimeMillis()+1000;}if(System.currentTimeMillis()<at)return;at=System.currentTimeMillis()+1000;try{if(step>0){var dir=new java.io.File(output);dir.mkdirs();net.minecraft.client.Screenshot.grab(dir,"bugs-"+step+".png",mc.getMainRenderTarget(),m->{});}int item=step++%6;switch(item){
 case 0->{mc.options.guiScale().set(1+(step-1)/6);mc.resizeDisplay();var title=new TitleScreen();popup=new CoreVersionsPopup(title);mc.setScreen(popup);var button=Button.builder(Component.literal("Minecraft"),b->{}).bounds(0,0,100,20).build();UiTheme.rendering(title,()->{if(UiTheme.stylesButtons(popup,button))throw new IllegalStateException("Modal styles Minecraft parent");});if(!UiTheme.stylesButtons(popup,button))throw new IllegalStateException("Render context leaked");}
 case 1->{var q=new JsonObject();q.addProperty("amount",1);mc.setScreen(new TaskEditScreen(null,"Количество",List.of(new CommunityScreen.Field("amount","Количество",8)),q,j->{}));var input=(EditBox)mc.screen.children().stream().filter(c->c instanceof EditBox).findFirst().orElseThrow();if(!input.getValue().equals("1"))throw new IllegalStateException("Numeric resource preset lost");}
 case 2->{var rows=new JsonArray();mc.setScreen(new TaskResourcesScreen(null,rows,j->{}));press("+ Выбрать предмет");}
 case 3->{if(!(mc.screen instanceof ItemPickerScreen))throw new IllegalStateException("Item picker missing");for(var child:mc.screen.children())if(child instanceof Button b&&b.active){b.onPress();break;}if(!(mc.screen instanceof TaskEditScreen))throw new IllegalStateException("Item did not open amount editor");}
 case 4->{var screen=new ScrollScreen(Component.literal("Scroll test")){protected void init(){scrollArea(300,new dev.abros.anthub.core.NativeLayout.Box(20,40,186,Math.max(0,height-80)),24);addRenderableWidget(Button.builder(Component.literal("Row"),b->{}).bounds(20,40,170,20).build());}};mc.setScreen(screen);screen.mouseScrolled(50,60,0,-0.1);screen.mouseScrolled(50,60,0,-0.1);screen.mouseScrolled(50,60,0,-0.2);if(screen.firstRow!=1)throw new IllegalStateException("Fractional scroll lost");if(screen.mouseScrolled(50,10,0,-1)||screen.mouseScrolled(210,60,0,-1)||screen.mouseScrolled(50,60,0,0))throw new IllegalStateException("Scroll escaped viewport");screen.mouseClicked(201,screen.height-45,0);screen.mouseReleased(201,screen.height-45,0);if(screen.firstRow<=1)throw new IllegalStateException("Scrollbar cannot reach end");}
 case 5->{var lib=new JsonObject();var entries=new JsonArray();for(int n=0;n<3;n++){var row=new JsonObject();row.addProperty("id",new UUID(0,n+1).toString());row.addProperty("name","Скин "+n);row.addProperty("hash","");row.addProperty("slim",false);entries.add(row);}lib.add("entries",entries);SkinClient.library=lib;SkinClient.busy=false;dev.abros.anthub.network.Protocol.supportedFeatures=Set.of("skins","skin-names","skin-order");var screen=new SkinsScreen(null);mc.setScreen(screen);long arrows=screen.children().stream().filter(c->c instanceof Button b&&Set.of("↑","↓").contains(b.getMessage().getString())).count();if(arrows<2)throw new IllegalStateException("Reorder controls missing");}
 }if(step==18){System.out.println("ANTHUB_BUGS_UI_OK: resource add, numeric preset, parent styling, fractional and bounded scroll, skin ordering at GUI 1–3");NextUiHarness.openPreview();}}catch(Throwable err){step=18;System.out.println("ANTHUB_BUGS_UI_FAILED");err.printStackTrace();}}
 private static void press(String label){for(var c:Minecraft.getInstance().screen.children())if(c instanceof Button b&&b.getMessage().getString().equals(label)){b.onPress();return;}throw new IllegalStateException("Missing "+label);}
}
