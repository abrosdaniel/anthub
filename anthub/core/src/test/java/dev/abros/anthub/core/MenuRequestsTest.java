package dev.abros.anthub.core;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MenuRequestsTest {
 private JsonObject request(String action,String op){var q=new JsonObject();q.addProperty("action",action);q.addProperty("op",op);return q;}
 @Test void readsNeverReceiveMutationIdentity(){for(String op:java.util.List.of("workGet","plusProfile","plusIgnores","plusItemRead","toolsMapPeers")){var q=request("community",op);assertTrue(MenuRequests.read(q));assertFalse(MenuRequests.mutation(q));}assertTrue(MenuRequests.read(request("moderationVote","view")));assertFalse(MenuRequests.read(request("moderationVote","vote")));assertTrue(MenuRequests.mutation(request("community","workSave")));assertFalse(MenuRequests.read(request("reply","")));}
 @Test void readCoalescingPreservesEveryFilterAndPage(){var first=request("players","");first.addProperty("query","Player");first.addProperty("all",false);first.addProperty("request","first");var second=first.deepCopy();second.addProperty("request","second");assertTrue(MenuRequests.sameRead(first,second));for(String key:java.util.List.of("all","status","page","group","cursor")){var changed=second.deepCopy();changed.addProperty(key,"changed");assertFalse(MenuRequests.sameRead(first,changed),key);}assertFalse(MenuRequests.sameRead(request("reply",""),request("reply","")));}
}
