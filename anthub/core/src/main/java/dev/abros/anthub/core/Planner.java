package dev.abros.anthub.core;
import java.nio.file.*;
import java.io.*;
import java.util.*;
public final class Planner {
    public record Owned(String hash,String policy,String componentId){}
    public record Change(String path,String before,String after){}
    public record Plan(String id,String projectKey,List<Change> changes,Map<String,Owned> ownership,Set<String> selection,List<String> conflicts,long downloadBytes){}
    public static Plan plan(Path game,Manifest target,Set<String> selected,Map<String,Owned> old,Cache cache)throws IOException{
        return plan(game,target,selected,old,cache,false);
    }
    public static Plan plan(Path game,Manifest target,Set<String> selected,Map<String,Owned> old,Cache cache,boolean preserveModifiedInBackup)throws IOException{
        return plan(game,target,selected,old,cache,preserveModifiedInBackup,Set.of());
    }
    public static Plan plan(Path game,Manifest target,Set<String> selected,Map<String,Owned> old,Cache cache,boolean preserveModifiedInBackup,Set<String> approvedReplacements)throws IOException{
        return plan(game,target,selected,old,cache,preserveModifiedInBackup,approvedReplacements,Set.of());
    }
    public static boolean configurable(String path){return path.startsWith("config/")||path.startsWith("defaultconfigs/");}
    public static Plan plan(Path game,Manifest target,Set<String> selected,Map<String,Owned> old,Cache cache,boolean preserveModifiedInBackup,Set<String> approvedReplacements,Set<String> kept)throws IOException{
        Set<String> choice=new Selection(target).resolve(selected);Map<String,Manifest.FileEntry> desired=new TreeMap<>();
        target.files().stream().filter(f->choice.contains(f.componentId())).forEach(f->desired.put(f.path(),f));
        List<Change> changes=new ArrayList<>();List<String> conflicts=new ArrayList<>();Map<String,Owned> owned=new TreeMap<>();Set<String> hashes=new HashSet<>();long bytes=0;
        for(var e:desired.entrySet()){
            String path=e.getKey();var f=e.getValue();Path dest=SafePaths.resolve(game,path);String current=hash(dest);Owned prev=old.get(path);
            if(current!=null&&configurable(path)&&!f.policy().equals("enforce")&&(kept.contains(path)||(prev!=null&&prev.policy().equals("preserve")&&!approvedReplacements.contains(path)))){
                owned.put(path,new Owned(current,"preserve",f.componentId()));continue;
            }
            if(current!=null&&approvedReplacements.contains(path)){
                owned.put(path,new Owned(f.sha256(),f.policy(),f.componentId()));
                if(!current.equals(f.sha256())){changes.add(new Change(path,current,f.sha256()));if(hashes.add(f.sha256())&&!cache.contains(f.sha256()))bytes+=f.size();}continue;
            }
            if(current!=null&&prev==null){
                if(!approvedReplacements.contains(path)){conflicts.add("USER_FILE_COLLISION: "+path);continue;}
                owned.put(path,new Owned(f.sha256(),f.policy(),f.componentId()));
                if(!current.equals(f.sha256())){changes.add(new Change(path,current,f.sha256()));if(hashes.add(f.sha256())&&!cache.contains(f.sha256()))bytes+=f.size();}continue;
            }
            if(current!=null&&prev!=null&&!current.equals(prev.hash())){
                if(f.policy().equals("enforce")){if(!preserveModifiedInBackup){conflicts.add("MODIFIED_MANAGED_FILE: "+path);continue;}}
                else {owned.put(path,prev);continue;}
            }
            if(current!=null&&f.policy().equals("preserve")){owned.put(path,prev);continue;}
            owned.put(path,new Owned(f.sha256(),f.policy(),f.componentId()));
            if(!Objects.equals(current,f.sha256())){changes.add(new Change(path,current,f.sha256()));if(hashes.add(f.sha256())&&!cache.contains(f.sha256()))bytes+=f.size();}
        }
        for(var e:old.entrySet())if(!desired.containsKey(e.getKey())){
            String current=hash(SafePaths.resolve(game,e.getKey()));
            if(current==null)continue;
            if((!current.equals(e.getValue().hash())||e.getValue().policy().equals("preserve"))&&!preserveModifiedInBackup){conflicts.add("PRESERVED_REMOVAL: "+e.getKey());continue;}
            changes.add(new Change(e.getKey(),current,null));
        }
        return new Plan(UUID.randomUUID().toString(),target.projectKey(),List.copyOf(changes),Map.copyOf(owned),choice,List.copyOf(conflicts),bytes);
    }
    public static String hash(Path p)throws IOException{if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return null;if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Not a regular file: "+p);return Hashes.sha256(p);}
}
