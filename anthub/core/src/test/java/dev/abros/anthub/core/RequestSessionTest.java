package dev.abros.anthub.core;
import com.google.gson.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class RequestSessionTest {
 @Test void retriesKeepOperationButRejectLateReplies(){var s=new RequestSession();var payload=new JsonObject();payload.addProperty("text","hello");var first=s.begin(payload,true,100);assertThrows(IllegalStateException.class,()->s.begin(payload,true,101));assertTrue(s.timeout(15100));var retry=s.retry(15101);assertEquals(first.get("operationId"),retry.get("operationId"));assertNotEquals(first.get("request"),retry.get("request"));assertFalse(s.receive(first));assertTrue(s.receive(retry));assertFalse(s.receive(retry));}
 @Test void cancellationRejectsResponse(){var s=new RequestSession();var sent=s.begin(new JsonObject(),false,1);s.cancel();assertFalse(s.receive(sent));assertFalse(sent.has("operationId"));}
}
