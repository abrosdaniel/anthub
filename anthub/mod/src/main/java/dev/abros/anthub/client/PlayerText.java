package dev.abros.anthub.client;
import com.google.gson.JsonObject;
import dev.abros.anthub.core.*;
import net.minecraft.network.chat.*;
/** Display-only metadata: never used as command arguments or identity. */
final class PlayerText {
 private PlayerText(){}
 static MutableComponent text(String text){var result=Component.empty();for(var span:LegacyText.parse(text)){var style=Style.EMPTY.withBold(span.bold()).withItalic(span.italic()).withUnderlined(span.underlined()).withStrikethrough(span.strike()).withObfuscated(span.obfuscated());if(span.color()!=null)style=style.withColor(span.color());result.append(Component.literal(span.text()).withStyle(style));}return result;}
 static MutableComponent name(JsonObject player){return text(Json.opt(player,"prefix","")+Json.opt(player,"name","Игрок")+Json.opt(player,"suffix",""));}
}
