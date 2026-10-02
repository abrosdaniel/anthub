package dev.abros.anthub.core.compat;

import java.util.*;
import java.util.function.Function;

/** A frozen preview. Only verified aliases may be replaced; unknown UUIDs survive. */
public record AccessRepairPlan(Set<UUID> before, Set<UUID> after, Map<UUID,UUID> replacements) {
 public AccessRepairPlan { before=Set.copyOf(before);after=Set.copyOf(after);replacements=Map.copyOf(replacements); }
 public static AccessRepairPlan preview(Set<UUID> entries,Function<UUID,Optional<UUID>> verifiedAlias) {
  var result=new HashSet<>(entries);var changes=new LinkedHashMap<UUID,UUID>();
  for(var id:entries){var target=verifiedAlias.apply(id);if(target.isPresent()&&!target.get().equals(id)){result.remove(id);result.add(target.get());changes.put(id,target.get());}}
  return new AccessRepairPlan(entries,result,changes);
 }
 public boolean matches(Set<UUID> current){return before.equals(current);}
}
