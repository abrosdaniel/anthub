package dev.abros.anthub.client;
import com.google.gson.JsonObject;
import dev.abros.anthub.core.*;
import net.minecraft.client.Minecraft;
import java.util.function.Consumer;
/** Shared account/server-scoped draft persistence. Disk work never runs on the render thread. */
final class UiFormDraft {
 private final DraftStore store;private final String server,account,context;
 UiFormDraft(String context){var mc=Minecraft.getInstance();store=new DraftStore(mc.gameDirectory.toPath().resolve("anthub/drafts"));server=mc.getCurrentServer()==null?"":mc.getCurrentServer().ip;account=Json.opt(ServerMenuClient.state,"uuid","");this.context=context;}
 void restore(Consumer<JsonObject> ready,Consumer<String> error){Client.IO.submit(()->{try{var saved=store.load(server,account,context);Minecraft.getInstance().execute(()->ready.accept(saved));}catch(Exception ex){Minecraft.getInstance().execute(()->error.accept("Не удалось восстановить черновик"));}});}
 void save(JsonObject fields,Consumer<String> error){var snapshot=fields.deepCopy();Client.IO.submit(()->{try{store.save(server,account,context,snapshot);}catch(Exception ex){Minecraft.getInstance().execute(()->error.accept("Не удалось сохранить черновик"));}});}
 void remove(Consumer<String> error){Client.IO.submit(()->{try{store.remove(server,account,context);}catch(Exception ex){Minecraft.getInstance().execute(()->error.accept("Не удалось удалить черновик"));}});}
}
