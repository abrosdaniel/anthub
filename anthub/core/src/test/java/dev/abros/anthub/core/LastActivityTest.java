package dev.abros.anthub.core;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LastActivityTest {
 @Test void presenceUsesRelativeTimeThenLocalDate(){long now=Instant.parse("2026-10-01T12:00:00Z").toEpochMilli();var zone=ZoneId.of("Europe/Warsaw");
  assertEquals("Был только что",LastActivity.text(now+1000,now,zone));
  assertEquals("Был 1 минуту назад",LastActivity.text(now-60000,now,zone));
  assertEquals("Был 22 минуты назад",LastActivity.text(now-22*60000,now,zone));
  assertEquals("Был 11 минут назад",LastActivity.text(now-11*60000,now,zone));
  assertEquals("Был 1 час назад",LastActivity.text(now-3600000,now,zone));
  assertEquals("Был 23 часа назад",LastActivity.text(now-23*3600000L,now,zone));
  assertEquals("Был 30.09.2026 в 14:00",LastActivity.text(now-86400000,now,zone));
  assertEquals("Был давно",LastActivity.text(now-30L*86400000,now,zone));assertEquals("Был давно",LastActivity.text(0,now,zone));
 }
}
