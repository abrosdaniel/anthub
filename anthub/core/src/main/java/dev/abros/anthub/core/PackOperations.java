package dev.abros.anthub.core;

import java.util.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Installation use cases independent of Minecraft screens and rendering. */
public final class PackOperations {
    public record Review(RepositoryClient.Release release, Set<String> selection,
                         Set<String> replacements, Set<String> kept, Map<String,String> observed, Planner.Plan plan) {}
    private final Hub hub;
    private final Executor executor;
    public PackOperations(Hub hub, Executor executor) {this.hub=hub;this.executor=executor;}
    public CompletableFuture<Review> review(RepositoryClient.Release release,Set<String> selection,AtomicBoolean cancelled){return review(release,selection,Map.of(),cancelled);}
    public CompletableFuture<Review> review(RepositoryClient.Release release,Set<String> selection,Map<String,Boolean> keepChoices,AtomicBoolean cancelled){
        Set<String> snapshot=Set.copyOf(selection);Map<String,Boolean> decisions=Map.copyOf(keepChoices);
        return submit(cancelled,()->{
            Planner.Plan initial=hub.plan(release,snapshot,true);
            Set<String> replacements=new LinkedHashSet<>(),kept=new LinkedHashSet<>();Map<String,String> observed=new LinkedHashMap<>();
            for(String conflict:initial.conflicts()){
                if(!conflict.startsWith("USER_FILE_COLLISION: "))throw new IllegalStateException(conflict);
                replacements.add(conflict.substring("USER_FILE_COLLISION: ".length()));
            }
            // Existing config files always get an explicit choice, including managed edits.
            Set<String> resolved=new Selection(release.manifest()).resolve(snapshot);
            for(var file:release.manifest().files())if(resolved.contains(file.componentId())){
                String current=Planner.hash(SafePaths.resolve(hub.game,file.path()));
                if(current!=null && (replacements.contains(file.path()) || (Planner.configurable(file.path())&&!current.equals(file.sha256())))){
                    observed.put(file.path(),current);
                    if(Planner.configurable(file.path())&&!file.policy().equals("enforce")&&decisions.getOrDefault(file.path(),true)){kept.add(file.path());replacements.remove(file.path());}
                    else replacements.add(file.path());
                }
            }
            Planner.Plan plan=hub.plan(release,snapshot,replacements,kept);
            if(!plan.conflicts().isEmpty())throw new IllegalStateException(String.join("\n",plan.conflicts()));
            return new Review(release,snapshot,Set.copyOf(replacements),Set.copyOf(kept),Map.copyOf(observed),plan);
        });
    }
    public CompletableFuture<String> compare(Review review,String path,AtomicBoolean cancelled){return submit(cancelled,()->{
        var entry=review.release().manifest().files().stream().filter(f->f.path().equals(path)).findFirst().orElseThrow();
        if(!Planner.configurable(path))throw new IllegalArgumentException("Only configuration files can be compared");
        Path local=SafePaths.resolve(hub.game,path);if(entry.size()>256*1024||Files.size(local)>256*1024)throw new IllegalArgumentException("Configuration is too large to compare (256 KiB limit)");
        Path desired=hub.cache.obtain(entry,cancelled);
        return ConfigDiff.compare(Files.readString(local),Files.readString(desired));
    });}
    public CompletableFuture<String> install(Review review,AtomicBoolean cancelled,Consumer<String> progress){return submit(cancelled,()->{
        for(var entry:review.observed().entrySet())if(!entry.getValue().equals(Planner.hash(SafePaths.resolve(hub.game,entry.getKey()))))throw new IllegalStateException("Installation plan changed; review the changes again");
        Planner.Plan current=hub.plan(review.release(),review.selection(),review.replacements(),review.kept());
        if(!current.conflicts().isEmpty()||!current.changes().equals(review.plan().changes())||!current.ownership().equals(review.plan().ownership()))throw new IllegalStateException("Installation plan changed; review the changes again");
        if(cancelled.get())throw new CancellationException();return hub.stage(review.release(),current,cancelled,progress);
    });}
    private <T> CompletableFuture<T> submit(AtomicBoolean cancelled,Work<T> work){return CompletableFuture.supplyAsync(()->{if(cancelled.get())throw new CancellationException();try{return work.run();}catch(Exception failure){throw new CompletionException(failure);}},executor);}
    @FunctionalInterface private interface Work<T>{T run()throws Exception;}
}
