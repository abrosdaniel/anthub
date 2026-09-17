package dev.abros.anthub.client;
import com.google.gson.*;
import dev.abros.anthub.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.List;
final class LocationActions {
 static void render(CommunityScreen screen,JsonObject record){
  if(!record.has("location"))return;var place=CommunityLocation.read(record.getAsJsonObject("location"));screen.text("Место: "+place.name());screen.text(place.coordinates());
  screen.action("Скопировать координаты",()->Minecraft.getInstance().keyboardHandler.setClipboard(place.coordinates()));
  if(XaeroMapBridge.available())screen.action("Открыть на карте",()->{try{XaeroMapBridge.open(screen.surface(),place);}catch(Exception|LinkageError ex){Minecraft.getInstance().setScreen(new TextScreen(screen.surface(),Component.literal("Карта Xaero"),ex instanceof IllegalStateException?ex.getMessage():"Эта версия Xaero World Map не поддерживает открытие точки. Координаты можно скопировать из карточки."));}});
  if(XaeroBridge.available())screen.action("Метка в Xaero…",()->Minecraft.getInstance().setScreen(new ChoicePopup(screen.surface(),"Как добавить место?",List.of("Идти сюда","На этот сеанс","Сохранить место"),type->{try{XaeroBridge.edit(screen.surface(),place,type);}catch(Exception|LinkageError ex){Minecraft.getInstance().setScreen(new TextScreen(screen.surface(),Component.literal("Место в Xaero"),"Не удалось открыть редактор Xaero. "+(ex instanceof IllegalStateException?ex.getMessage():"Проверьте совместимость версии мода.")+"\nКоординаты можно скопировать из карточки."));}})));
 }
}
