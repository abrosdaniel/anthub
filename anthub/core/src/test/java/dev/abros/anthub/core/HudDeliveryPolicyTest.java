package dev.abros.anthub.core;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class HudDeliveryPolicyTest {
 @Test void categoryOverrideDoesNotEnableOtherCategories(){var settings=Set.of("event:comment","allow:groups:comment");assertTrue(HudDeliveryPolicy.allowed(settings,"groups","comment",false));assertFalse(HudDeliveryPolicy.allowed(settings,"board","comment",false));}
 @Test void categoryDisableWinsOverGlobalDefaultAndExplicitAllow(){assertFalse(HudDeliveryPolicy.allowed(Set.of("groups","allow:groups:invite"),"groups","invite",true));assertFalse(HudDeliveryPolicy.allowed(Set.of("groups:invite","allow:invite"),"groups","invite",true));}
 @Test void explicitGlobalEnableAndDefaultBehaveIndependently(){assertTrue(HudDeliveryPolicy.allowed(Set.of("allow:comment"),"board","comment",false));assertTrue(HudDeliveryPolicy.allowed(Set.of(),"groups","invite",true));assertFalse(HudDeliveryPolicy.allowed(Set.of(),"groups","comment",false));}
}
