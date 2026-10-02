package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.*;
/** Client-only labels from actor-scoped snapshots. Depth-tested and expired; no world entities. */
final class StockHologramRenderer {
 private static long expires;private static JsonArray panels=new JsonArray();
 static void receive(JsonObject snapshot){panels=snapshot.has("holograms")?snapshot.getAsJsonArray("holograms").deepCopy():new JsonArray();expires=net.minecraft.Util.getMillis()+15000;}
 static void clear(){panels=new JsonArray();expires=0;}
 static void render(RenderLevelStageEvent event){var mc=Minecraft.getInstance();if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES||mc.level==null||mc.player==null||mc.options.hideGui||!HudSettings.INSTANCE.holograms||net.minecraft.Util.getMillis()>expires)return;var camera=event.getCamera();var location=camera.getPosition();var buffers=mc.renderBuffers().bufferSource();
  for(var e:panels){var row=e.getAsJsonObject();if(!Json.str(row,"dimension").equals(mc.level.dimension().location().toString()))continue;double x=row.get("x").getAsDouble(),y=row.get("y").getAsDouble(),z=row.get("z").getAsDouble();if(location.distanceToSqr(x,y,z)>24*24)continue;if(!mc.level.hasChunkAt(net.minecraft.core.BlockPos.containing(x,y,z)))continue;var lines=new ArrayList<String>();lines.add(Json.str(row,"title"));for(var item:row.getAsJsonArray("resources")){var res=item.getAsJsonObject();long missing=res.get("missing").getAsLong();lines.add(TaskResourcesScreen.name(Json.str(res,"item"))+" · "+(missing==0?"Собрано":"Ещё "+missing)+" · "+res.get("have")+" / "+res.get("amount"));}if(row.get("remainingTypes").getAsInt()>0)lines.add("Ещё ресурсов: "+row.get("remainingTypes"));var matrix=new Matrix4f(event.getModelViewMatrix()).translate((float)(x-location.x),(float)(y-location.y),(float)(z-location.z)).rotate(camera.rotation()).scale(-.025f,-.025f,.025f);int offset=-lines.size()*10;for(String line:lines){line=UiKit.fit(mc.font,line,240);mc.font.drawInBatch(Component.literal(line),-mc.font.width(line)/2f,offset,0xFFE0E9EE,false,matrix,buffers,Font.DisplayMode.NORMAL,0xA020252A,15728880);offset+=10;}}
 }
 private StockHologramRenderer(){}
}
