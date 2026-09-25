package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UiPayloadTest {
 @Test void onlyCorrelationIsIgnored()throws Exception{var first=Json.parse("{\"request\":\"a\",\"entries\":[{\"id\":\"1\",\"title\":\"A\"}],\"nextCursor\":\"next\"}");var next=first.deepCopy();next.addProperty("request","b");assertTrue(UiPayload.same(first,next));next.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("title","B");assertFalse(UiPayload.same(first,next));}
 @Test void PermissionsAndPaginationMustInvalidate()throws Exception{var first=Json.parse("{\"manage\":false,\"entries\":[]}");var next=first.deepCopy();next.addProperty("manage",true);assertFalse(UiPayload.same(first,next));next=first.deepCopy();next.addProperty("nextCursor","page2");assertFalse(UiPayload.same(first,next));assertFalse(UiPayload.same(next,first));}
 @Test void ObviousActionsHaveNoHint(){for(String label:new String[]{"Принять","Отклонить","Назад","Отправить","Сохранить"})assertEquals("",UiHelp.text(label));assertTrue(UiHelp.text("Ограничить").contains("чат"));assertTrue(UiHelp.text("Второй слой: Вкл").contains("предпросмотре"));}
}
