package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LegacyTextTest {
 @Test void colorsResetStyles(){var s=LegacyText.parse("&c&lAdmin&7 Player&r!");assertEquals(3,s.size());assertTrue(s.get(0).bold());assertEquals(0xFF5555,s.get(0).color());assertFalse(s.get(1).bold());assertNull(s.get(2).color());}
 @Test void hexAndSectionCodes(){assertEquals(0x12ABEF,LegacyText.parse("&#12abefName").getFirst().color());assertEquals(0x12ABEF,LegacyText.parse("§x§1§2§a§b§e§fName").getFirst().color());assertTrue(LegacyText.parse("§oName").getFirst().italic());}
 @Test void plainAndMalformedStayVisible(){assertEquals("A&Z &#oops &x",LegacyText.parse("A&Z &#oops &x").stream().map(LegacyText.Span::text).reduce("",String::concat));}
 @Test void luckPermsHexTags(){var spans=LegacyText.parse("<#FF5555>[Root] ABROSxd<#1E90FF> @abrosdaniel");assertEquals(2,spans.size());assertEquals("[Root] ABROSxd",spans.get(0).text());assertEquals(0xFF5555,spans.get(0).color());assertEquals(0x1E90FF,spans.get(1).color());}
 @Test void closingHexTagResetsColor(){var spans=LegacyText.parse("<#ff5555>Root</#ff5555> Player");assertNull(spans.get(1).color());}
}
