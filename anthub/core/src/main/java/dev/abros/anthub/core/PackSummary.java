package dev.abros.anthub.core;
import java.util.*;
/** Derived from existing manifests, without any changes to seed format. */
public final class PackSummary {
    public record Summary(List<String> added,List<String> updated,List<String> removed,List<String> required){}
    public static Summary compare(Manifest old,Manifest next){
        var previous=new LinkedHashMap<String,Manifest.Component>();if(old!=null&&old.repository().equals(next.repository()))for(var c:old.components())previous.put(c.id(),c);
        List<String> added=new ArrayList<>(),updated=new ArrayList<>(),removed=new ArrayList<>(),required=new ArrayList<>();
        for(var c:next.components()){
            var before=previous.remove(c.id());
            if(before==null)added.add(c.name());
            else if(!fingerprint(old,c.id()).equals(fingerprint(next,c.id()))||!before.dependencies().equals(c.dependencies()))updated.add(c.name());
            if(c.kind().equals("required")&&before!=null&&!before.kind().equals("required"))required.add(c.name());
        }
        for(var c:previous.values())removed.add(c.name());
        return new Summary(List.copyOf(added),List.copyOf(updated),List.copyOf(removed),List.copyOf(required));
    }
    private static Set<String> fingerprint(Manifest m,String id){var out=new HashSet<String>();for(var f:m.files())if(f.componentId().equals(id))out.add(f.path()+":"+f.sha256());return out;}
    public static String lockedReason(Manifest m,String id){
        var selected=new Selection(m);if(!selected.resolve(Set.of()).contains(id))return "";
        for(var c:m.components())if(c.id().equals(id)&&c.kind().equals("required"))return "Обязательный для сервера";
        List<String> roots=new ArrayList<>();for(var c:m.components())if(c.kind().equals("required")&&depends(m,c.id(),id,new HashSet<>()))roots.add(c.name());
        return "Нужен для: "+String.join(", ",roots);
    }
    private static boolean depends(Manifest m,String root,String target,Set<String> visited){
        if(!visited.add(root))return false;for(var c:m.components())if(c.id().equals(root))for(String child:c.dependencies())if(child.equals(target)||depends(m,child,target,visited))return true;return false;
    }
}
