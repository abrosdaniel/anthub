package dev.abros.anthub.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
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
        return hub.requests.submit("project:"+Repositories.normalize(repository)+":"+refreshDetails,executor,() -> { if(refreshDetails)hub.details.refresh(repository); return hub.repositories.fetchOrCached(repository); });
    }

    public CompletableFuture<String> content(Manifest manifest, String kind) {
        return hub.requests.submit("content:"+manifest.repository()+":"+manifest.version()+":"+kind,executor,() -> hub.content(manifest, kind));
    }

    public CompletableFuture<List<String>> catalog() {
        return hub.requests.submit("registry",executor,() -> hub.repositories.registry(
            "https://raw.githubusercontent.com/abrosdaniel/anthub/HEAD/anthub/registry/projects.json"));
    }

}
