package dev.abros.anthub.core.compat;
import dev.abros.anthub.core.auth.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AccessRepairPlanTest {
 @Test void unknownNameNeverInventsAnAccount(){var index=new ServerIdentities(List.of());assertTrue(index.known("poposha").isEmpty());assertTrue(index.known(UUID.randomUUID()).isEmpty());}
 @Test void offlineAndCaseLookupKeepServerIdentity(){var id=UUID.randomUUID();var index=new ServerIdentities(List.of(new AuthStore.Profile("poposha",id)));assertEquals(id,index.known("POPOSHA").orElseThrow().uuid());assertEquals("poposha",index.known(id).orElseThrow().name());}
 @Test void renameRemovesOldNameAndKeepsUuid(){var id=UUID.randomUUID();var index=new ServerIdentities(List.of(new AuthStore.Profile("ABROSxd",id)));index.remember(new AuthStore.Profile("ABR0S",id));assertTrue(index.known("ABROSxd").isEmpty());assertEquals(id,index.known("ABR0S").orElseThrow().uuid());}
 @Test void verifiedAliasSurvivesRestartButNeverReplacesServerUuid(){var id=UUID.randomUUID();var official=UUID.randomUUID();var p=new AuthStore.Profile("Name",id,official);for(var index:List.of(new ServerIdentities(List.of(p)),new ServerIdentities(List.of(p)))){assertEquals(id,index.verifiedAlias(official).orElseThrow().uuid());assertEquals(id,index.known("Name").orElseThrow().uuid());}}
 @Test void unknownEntriesArePreservedAndDuplicatesCollapse(){var id=UUID.randomUUID();var alias=UUID.randomUUID();var unknown=UUID.randomUUID();var plan=AccessRepairPlan.preview(Set.of(id,alias,unknown),u->u.equals(alias)?Optional.of(id):Optional.empty());assertEquals(Set.of(id,unknown),plan.after());assertEquals(Map.of(alias,id),plan.replacements());}
 @Test void stalePreviewIsRejected(){var a=UUID.randomUUID();var b=UUID.randomUUID();var plan=AccessRepairPlan.preview(Set.of(a),u->Optional.of(b));assertTrue(plan.matches(Set.of(a)));assertFalse(plan.matches(Set.of(a,b)));}
 @Test void unlinkStopsRepair(){var id=UUID.randomUUID();var alias=UUID.randomUUID();var index=new ServerIdentities(List.of(new AuthStore.Profile("Name",id,alias)));index.remember(new AuthStore.Profile("Name",id));var plan=AccessRepairPlan.preview(Set.of(alias),u->index.verifiedAlias(u).map(AuthStore.Profile::uuid));assertEquals(Set.of(alias),plan.after());assertTrue(plan.replacements().isEmpty());}
 @Test void previewIsImmutable(){var a=UUID.randomUUID();var before=new HashSet<>(Set.of(a));var plan=AccessRepairPlan.preview(before,u->Optional.empty());before.clear();assertEquals(Set.of(a),plan.before());assertThrows(UnsupportedOperationException.class,()->plan.after().clear());}
}
