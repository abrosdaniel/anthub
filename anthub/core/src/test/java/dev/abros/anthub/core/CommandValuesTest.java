package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommandValuesTest {
 @Test void durationAcceptsOnlyBoundedExplicitUnits(){assertEquals(433800000,CommandValues.duration("120h30m"));assertEquals(0,CommandValues.duration("0m"));for(String value:new String[]{"","-1h","5","1m2h","1h2h","999999999999999999999h","1.5h","1H"})assertThrows(IllegalArgumentException.class,()->CommandValues.duration(value),value);}
 @Test void datesAreStrictAndNeverFuture(){assertTrue(CommandValues.date("2026-01-01")>0);for(String value:new String[]{"2026-02-30","2026-1-1","9999-01-01","1960-01-01","today"})assertThrows(IllegalArgumentException.class,()->CommandValues.date(value),value);}
}
