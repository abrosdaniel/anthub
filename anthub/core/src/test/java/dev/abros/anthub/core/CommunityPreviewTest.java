package dev.abros.anthub.core;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommunityPreviewTest {
 @Test void noPrivatePayloads(){var d=JsonParser.parseString("{\"section\":\"polls\",\"votes\":{\"player\":[1]},\"responses\":{\"player\":{\"text\":\"private\"}},\"applications\":{\"p\":\"secret\"},\"invitations\":{\"p\":1}}").getAsJsonObject();var p=CommunityPreview.of(d);assertFalse(p.has("votes"));assertFalse(p.has("responses"));assertFalse(p.has("applications"));assertFalse(p.has("invitations"));assertEquals(1,p.get("responsesCount").getAsInt());assertFalse(p.toString().contains("private"));}
 @Test void boundedPreview(){var d=new JsonObject();d.addProperty("description","x".repeat(500));d.addProperty("answer","a".repeat(500));var p=CommunityPreview.of(d);assertEquals(200,p.get("preview").getAsString().length());assertEquals(140,p.get("answerPreview").getAsString().length());}
}
