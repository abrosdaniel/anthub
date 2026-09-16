package dev.abros.anthub.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** Repository I/O without screen lifetime or rendering dependencies. */
public final class RepositoryOperations {
    private final Hub hub;
    private final Executor executor;

    public RepositoryOperations(Hub hub, Executor executor) {
        this.hub = hub;
        this.executor = executor;
    }

    public CompletableFuture<RepositoryClient.Release> fetch(String repository) {
        return fetch(repository,false);
    }

    public CompletableFuture<RepositoryClient.Release> fetch(String repository, boolean refreshDetails) {
        return submit(() -> { if(refreshDetails)hub.details.refresh(repository); return hub.repositories.fetchOrCached(repository); });
    }

    public CompletableFuture<String> content(Manifest manifest, String kind) {
        return submit(() -> hub.content(manifest, kind));
    }

    public CompletableFuture<List<String>> catalog() {
        return submit(() -> hub.repositories.registry(
            "https://raw.githubusercontent.com/abrosdaniel/anthub/HEAD/anthub/registry/projects.json"));
    }

    private <T> CompletableFuture<T> submit(Work<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try { return work.run(); }
            catch (Exception failure) { throw new CompletionException(failure); }
        }, executor);
    }
    @FunctionalInterface private interface Work<T> { T run() throws Exception; }
}
