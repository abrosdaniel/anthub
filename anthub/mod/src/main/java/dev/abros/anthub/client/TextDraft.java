package dev.abros.anthub.client;
import com.google.gson.JsonObject;
import dev.abros.anthub.core.*;
import net.minecraft.client.Minecraft;
/** Non-secret text draft shared by support forms; writes run on the serialized IO worker. */
final class TextDraft {
 private final DraftStore store;private final String server,account,context;
 TextDraft(String context){var mc=Minecraft.getInstance();store=new DraftStore(mc.gameDirectory.toPath().resolve("anthub/drafts"));server=mc.getCurrentServer()==null?"":mc.getCurrentServer().ip;account=Json.opt(ServerMenuClient.state,"uuid","");this.context=context;}
 String load(String fallback){try{return Json.opt(store.load(server,account,context),"text",fallback);}catch(Exception ex){return fallback;}}
 void save(String text){var data=new JsonObject();data.addProperty("text",text);Client.IO.submit(()->{try{store.save(server,account,context,data);}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot save support draft ({})",ex.getClass().getSimpleName());}});}
 void clear(){Client.IO.submit(()->{try{store.remove(server,account,context);}catch(Exception ignored){}});}
}
