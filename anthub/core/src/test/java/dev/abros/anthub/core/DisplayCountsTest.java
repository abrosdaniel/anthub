package dev.abros.anthub.core;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DisplayCountsTest {
 @Test void serverProfileNumericFields(){var state=JsonParser.parseString("{\"online\":1,\"maximum\":40}").getAsJsonObject();assertEquals("1",DisplayCounts.text(state,"online","—"));assertEquals("40",DisplayCounts.text(state,"maximum","—"));}
 @Test void cardCountsFromServerPreview(){var document=JsonParser.parseString("{\"responses\":{\"a\":{}},\"members\":{\"a\":\"leader\"},\"participants\":{\"a\":\"Alex\"},\"supporters\":{\"a\":true}}").getAsJsonObject();var preview=CommunityPreview.of(document);for(String key:new String[]{"responsesCount","membersCount","participantsCount","supportersCount"})assertEquals("1",DisplayCounts.text(preview,key,"0"));}
 @Test void invalidCountersDoNotCrashScreen(){for(String input:new String[]{"null","true","{}","[]","\"12\"","-1","1.5","1e100"}){var state=new JsonObject();state.add("online",JsonParser.parseString(input));assertEquals("—",DisplayCounts.text(state,"online","—"));}assertEquals("—",DisplayCounts.text(new JsonObject(),"online","—"));}
 @Test void stringParsingStillStrict(){var state=JsonParser.parseString("{\"online\":1}").getAsJsonObject();assertThrows(IllegalArgumentException.class,()->Json.opt(state,"online","—"));}
}
