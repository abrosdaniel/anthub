package dev.abros.anthub.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class PackOperationsTest {
    @TempDir Path game;
    private RepositoryClient.Release release() throws Exception {
        JsonObject json;
        try (var input=getClass().getResourceAsStream("/fixtures/lock.json")) {
            json=Json.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
        byte[] content="desired".getBytes(StandardCharsets.UTF_8);
        var file=json.getAsJsonArray("files").get(0).getAsJsonObject();
        file.addProperty("sha256",Hashes.sha256(content));file.addProperty("size",content.length);
        var manifest=Manifest.parse(json);
        return new RepositoryClient.Release(manifest,Json.GSON.toJson(json).getBytes(StandardCharsets.UTF_8),false,false,"");
    }
    @Test void cancelledQueuedReviewNeverRuns() throws Exception {
        var queued=new ArrayList<Runnable>();var hub=new Hub(game,"1.0.0-alpha.1","21.1.250");
        var service=new PackOperations(hub,queued::add);var cancellation=new AtomicBoolean();
        var result=service.review(release(),Set.of(),cancellation);cancellation.set(true);queued.getFirst().run();
        assertThrows(CompletionException.class,result::join);
        assertFalse(Files.exists(game.resolve("anthub/state.json")));
    }
    @Test void changedFilesRequireAnotherReview() throws Exception {
        var release=release();var hub=new Hub(game,"1.0.0-alpha.1","21.1.250");var service=new PackOperations(hub,Runnable::run);
        Path file=SafePaths.resolve(game,release.manifest().files().getFirst().path());Files.createDirectories(file.getParent());Files.writeString(file,"before");
        var review=service.review(release,Set.of(),new AtomicBoolean()).join();Files.writeString(file,"changed after consent");
        assertThrows(CompletionException.class,()->service.install(review,new AtomicBoolean(),s->{}).join());
        assertEquals("changed after consent",Files.readString(file));assertFalse(Files.exists(game.resolve("anthub/pending.json")));
    }
    @Test void identicalFilesInstallWithoutRestart() throws Exception {
        var release=release();var hub=new Hub(game,"1.0.0-alpha.1","21.1.250");var service=new PackOperations(hub,Runnable::run);
        Path file=SafePaths.resolve(game,release.manifest().files().getFirst().path());Files.createDirectories(file.getParent());Files.writeString(file,"desired");
        var review=service.review(release,Set.of(),new AtomicBoolean()).join();
        assertEquals("",service.install(review,new AtomicBoolean(),s->{}).join());assertNotNull(hub.active());
    }
}
