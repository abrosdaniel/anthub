package dev.abros.anthub.core;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ServerMenuDataTest {
 JsonObject menu(){return JsonParser.parseString("{\"links\":[{\"name\":\"Community\",\"url\":\"https://example.org/\"}]}").getAsJsonObject();}
 @Test void validatesAndCopies(){var input=menu();var copy=ServerMenuData.validate(input);input.add("links",new JsonArray());assertEquals(1,copy.getAsJsonArray("links").size());}
 @Test void rejectsPrivateFieldsAndOldEvents(){var input=menu();input.add("events",new JsonArray());assertThrows(IllegalArgumentException.class,()->ServerMenuData.validate(input));}
 @Test void rejectsUnsafeLinks(){var input=menu();input.getAsJsonArray("links").get(0).getAsJsonObject().addProperty("url","http://example.org");assertThrows(IllegalArgumentException.class,()->ServerMenuData.validate(input));}
 @Test void limitsLinks(){var input=menu();for(int i=0;i<12;i++)input.getAsJsonArray("links").add(input.getAsJsonArray("links").get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->ServerMenuData.validate(input));}
}
