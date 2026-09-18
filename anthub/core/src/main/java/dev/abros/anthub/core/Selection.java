package dev.abros.anthub.core;
import java.util.*;
public final class Selection {
    private final Map<String,Manifest.Component> components=new LinkedHashMap<>();
    public Selection(Manifest m){m.components().forEach(c->components.put(c.id(),c));}
    public void validate(){
        for(var c:components.values()){
            for(String id:c.dependencies())if(!components.containsKey(id))throw new IllegalArgumentException("Missing dependency: "+id);
            for(String id:c.conflicts())if(!components.containsKey(id))throw new IllegalArgumentException("Missing conflict: "+id);
            visit(c.id(),new HashSet<>(),new HashSet<>());
        }
        resolve(Set.of());for(String id:components.keySet())resolve(Set.of(id));
    }
    private void visit(String id,Set<String> visiting,Set<String> done){if(done.contains(id))return;if(!visiting.add(id))throw new IllegalArgumentException("Dependency cycle: "+id);for(String d:components.get(id).dependencies())visit(d,visiting,done);visiting.remove(id);done.add(id);}
    public Set<String> initial(){Set<String>s=new LinkedHashSet<>();s.addAll(resolve(Set.of()));for(var c:components.values()){var candidate=new LinkedHashSet<>(s);candidate.add(c.id());try{s=new LinkedHashSet<>(resolve(candidate));}catch(IllegalArgumentException conflict){/* Conflicting alternatives remain available for manual selection. */}}return resolve(s);}
    /** Apply saved preferences to the current pack, then enforce its requirements. */
    public Set<String> restore(Set<String> saved){
        Set<String> retained=new HashSet<>(saved);retained.retainAll(components.keySet());
        return resolve(retained);
    }
    public Set<String> resolve(Set<String> chosen){
        Set<String>s=new TreeSet<>(chosen);components.values().stream().filter(c->c.kind().equals("required")).forEach(c->s.add(c.id()));
        Deque<String> q=new ArrayDeque<>(s);while(!q.isEmpty()){String id=q.remove();var c=components.get(id);if(c==null)throw new IllegalArgumentException("Unknown component: "+id);for(String d:c.dependencies())if(s.add(d))q.add(d);}
        for(String id:s)for(String other:components.get(id).conflicts())if(s.contains(other))throw new IllegalArgumentException("Conflict: "+id+" / "+other);
        return Set.copyOf(s);
    }
}
