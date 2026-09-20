package dev.abros.anthub.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class SelectionUpdateTest {
    @TempDir Path game;
    private Manifest pack(boolean required) throws Exception {
        JsonObject json;
        try(var in=getClass().getResourceAsStream("/fixtures/lock.json")){
            json=Json.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
        var components=new JsonArray();
        for(String id:List.of("jade","library","kept","declined")){
            var c=new JsonObject();c.addProperty("id",id);c.addProperty("name",id);c.addProperty("description",id);c.addProperty("category","mods");
            c.addProperty("kind",id.equals("jade")&&required?"required":"optional");
            var dependencies=new JsonArray();if(id.equals("jade"))dependencies.add("library");
            c.add("dependencies",dependencies);c.add("conflicts",new JsonArray());components.add(c);
        }
        json.add("components",components);
        var f=json.getAsJsonArray("files").get(0).getAsJsonObject();
        f.addProperty("componentId","jade");f.addProperty("path","mods/jade.jar");f.addProperty("policy","enforce");
        json.getAsJsonObject("release").addProperty("version",required?"1.0.1":"1.0.0");
        return Manifest.parse(json);
    }
    private Hub installed() throws Exception {
        var state=new JsonObject();state.add("lock",pack(false).json());
        state.add("selection",Json.GSON.toJsonTree(Set.of("kept","removed")));
        Json.write(game.resolve("anthub/state.json"),state);
        return new Hub(game,"1.0.0","21.1.250");
    }
    @Test void activePackPromotesPreviouslyDeclinedModAndItsDependencies() throws Exception {
        var hub=installed();var target=pack(true);
        assertEquals(Set.of("jade","library","kept"),hub.choices(target));
        var plan=Planner.plan(game,target,hub.choices(target),Map.of(),hub.cache);
        assertEquals(List.of("mods/jade.jar"),plan.changes().stream().map(Planner.Change::path).toList());
        assertEquals(target.files().getFirst().size(),plan.downloadBytes());
    }
    @Test void inactivePackUsesSameReconciliation() throws Exception {
        var target=pack(true);var hub=new Hub(game,"1.0.0","21.1.250");
        Json.write(game.resolve("anthub/projects").resolve(target.projectKey()).resolve("local-state.json"),Map.of("selection",Set.of("kept","removed")));
        assertEquals(Set.of("jade","library","kept"),hub.choices(target));
    }
    @Test void optionalChoicesAreNotResetToDefaults() throws Exception {
        assertEquals(Set.of("kept"),installed().choices(pack(false)));
    }
    @Test void reviewEnforcesRequiredFilesEvenWithEmptyUiSelection() throws Exception {
        var target=pack(true);var hub=new Hub(game,"1.0.0","21.1.250");
        var release=new RepositoryClient.Release(target,Json.GSON.toJson(target.json()).getBytes(StandardCharsets.UTF_8),false,false,"");
        var review=new PackOperations(hub,Runnable::run).review(release,Set.of(),new AtomicBoolean()).join();
        assertEquals(Set.of("jade","library"),review.selection());
        assertEquals(review.selection(),review.plan().selection());
        assertTrue(review.plan().changes().stream().anyMatch(c->c.path().equals("mods/jade.jar")));
    }
    @Test void oldManifestDoesNotBlockHubAndPreservesChoices() throws Exception {
        var hub=installed();var state=hub.state();state.getAsJsonObject("lock").getAsJsonObject("anthub").remove("version");
        Json.write(game.resolve("anthub/state.json"),state);byte[] original=Files.readAllBytes(game.resolve("anthub/state.json"));
        hub=new Hub(game,"1.0.0","21.1.250");assertNull(hub.active());assertFalse(hub.recoveryMessage().isEmpty());
        assertEquals(Set.of("jade","library","kept"),hub.choices(pack(true)));
        assertArrayEquals(original,Files.readAllBytes(game.resolve("anthub/state.json")));
        try(var backups=Files.list(game.resolve("anthub/recovery"))){assertEquals(1,backups.count());}
    }
    @Test void unreadableStateBlocksInstallationButNotCatalog() throws Exception {
        Files.createDirectories(game.resolve("anthub"));Files.writeString(game.resolve("anthub/state.json"),"{broken");
        var hub=new Hub(game,"1.0.0","21.1.250");assertFalse(hub.recoveryMessage().isEmpty());
        hub.saveRepository("https://github.com/example/project");assertEquals(1,hub.saved().size());
        var release=new RepositoryClient.Release(pack(true),new byte[0],false,false,"");
        assertThrows(java.io.IOException.class,()->hub.plan(release,Set.of()));
        assertThrows(java.io.IOException.class,hub::clearUnusedCache);
        assertEquals("{broken",Files.readString(game.resolve("anthub/state.json")));
    }
    @Test void oneInvalidSavedRepositoryDoesNotBlockOthers() throws Exception {
        Json.write(game.resolve("anthub/preferences.json"),Map.of("repositories",List.of("not a repository","https://github.com/example/project")));
        var hub=new Hub(game,"1.0.0","21.1.250");assertEquals(List.of("https://github.com/example/project"),hub.saved());
    }
    @Test void summaryExplainsPromotionWithoutChangingSeed() throws Exception {
        var summary=PackSummary.compare(pack(false),pack(true));assertEquals(List.of("jade"),summary.required());
        assertEquals("Обязательный для сервера",PackSummary.lockedReason(pack(true),"jade"));
        assertEquals("Нужен для: jade",PackSummary.lockedReason(pack(true),"library"));
        assertEquals("",PackSummary.lockedReason(pack(true),"kept"));
    }
}
