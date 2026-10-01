package dev.abros.anthub.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VersionRequirementTest {
    @TempDir Path game;
    JsonObject lock()throws Exception {
        try(var in=getClass().getResourceAsStream("/fixtures/lock.json")){
            return Json.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    JsonObject project(String requirement)throws Exception {
        var json=Json.parse("{\"name\":\"sample\",\"version\":\"1.0.0\",\"server\":{\"address\":\"sample.org\"},\"minecraft\":{\"version\":\"1.21.1\",\"loaderVersion\":\"21.1.250\"},\"components\":[]}");
        json.addProperty("anthubVersion",requirement);return json;
    }
    @Test void majorAndMinimumRequirements(){
        for(String requirement:List.of("3.x","3.x.x")){
            assertTrue(Versions.supportsRequirement("3.0.0",requirement));
            assertTrue(Versions.supportsRequirement("3.99.99",requirement));
            assertFalse(Versions.supportsRequirement("4.0.0",requirement));
        }
        assertFalse(Versions.supportsRequirement("3.3.99","3.4.x"));
        assertTrue(Versions.supportsRequirement("3.4.0","3.4.x"));
        assertTrue(Versions.supportsRequirement("3.5.0","3.4.x"));
        assertFalse(Versions.supportsRequirement("3.4.1","3.4.2"));
        assertTrue(Versions.supportsRequirement("3.4.2","3.4.2"));
        assertTrue(Versions.supportsRequirement("3.5.0","3.4.2"));
        assertFalse(Versions.supportsRequirement("4.0.0","3.4.2"));
        assertFalse(Versions.supportsRequirement("3.4.0-beta","3.4.x"));
        assertFalse(Versions.supportsRequirement(null,"3.x.x"));
        assertFalse(Versions.supportsRequirement("3.4.0",null));
    }
    @Test void projectAndLockAgreeAndHubUsesMinimum()throws Exception {
        for(String requirement:List.of("3.x","3.x.x","3.4.x","3.4.2")){
            Schema.validate("project",project(requirement));
            var json=lock();json.getAsJsonObject("anthub").addProperty("version",requirement);
            var manifest=Manifest.parse(json);
            assertEquals(requirement,manifest.anthubVersion());
            assertEquals("",new Hub(game,"3.5.0","21.1.250").incompatibility(manifest));
            assertEquals("AntHub "+requirement,new Hub(game,"4.0.0","21.1.250").incompatibility(manifest));
            if(!requirement.contains(".x.x")&&!requirement.equals("3.x"))
                assertFalse(new Hub(game,"3.3.99","21.1.250").incompatibility(manifest).isEmpty());
        }
    }
    @Test void malformedRequirementsHaveActionableErrors()throws Exception {
        for(String requirement:List.of("3.4","3","03.x.x","3.04.x","3.4.02","3.x.2","3.X.X","^3.4.0","3.4.0-beta","3.4.x.x","")){
            assertFalse(Versions.supportsRequirement("3.5.0",requirement));
            var failure=assertThrows(IllegalArgumentException.class,()->Schema.validate("project",project(requirement)));
            assertTrue(failure.getMessage().contains("3.4.x"));
            var json=lock();json.getAsJsonObject("anthub").addProperty("version",requirement);
            assertThrows(IllegalArgumentException.class,()->Manifest.parse(json));
        }
    }
    @Test void serverRejectsTooOldClientEvenWithinCompatibleMajor()throws Exception {
        var json=lock();json.getAsJsonObject("anthub").addProperty("version","3.4.x");
        var manifest=Manifest.parse(json);
        var release=new RepositoryClient.Release(manifest,new byte[0],true,false,"","hash");
        var policy=new ServerProjectPolicy(manifest.repository(),true,release,"","","hash","digest");
        var state=new JsonObject();state.addProperty("repository",manifest.repository());state.addProperty("lockSha256","hash");
        state.addProperty("protocolVersion",WireProtocols.version("pack"));state.addProperty("requiredFilesDigest","digest");
        state.addProperty("coreVersion","3.3.99");assertFalse(policy.verify(state).isEmpty());
        state.addProperty("coreVersion","3.4.0");assertEquals("",policy.verify(state));
        state.addProperty("coreVersion","3.5.0");assertEquals("",policy.verify(state));
        state.addProperty("coreVersion","4.0.0");assertFalse(policy.verify(state).isEmpty());
    }
}
