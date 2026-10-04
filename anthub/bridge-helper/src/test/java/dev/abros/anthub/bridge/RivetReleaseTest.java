package dev.abros.anthub.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
class RivetReleaseTest {
    JsonObject descriptor(){return JsonParser.parseString("{\"schemaVersion\":1,\"version\":\"1.0.0\",\"artifacts\":[{\"minecraft\":\"1.21.1\",\"neoForge\":\"21.1.250\",\"java\":21,\"url\":\"https://github.com/abrosdaniel/rivet/releases/download/v1.0.0/rivet-1.0.0-mc1.21.1-neoforge.jar\",\"sha256\":\""+"a".repeat(64)+"\",\"size\":100}]}").getAsJsonObject();}
    @Test void acceptsPublishedCompatibleRivet()throws Exception{assertEquals("1.0.0",RivetRelease.parse("v1.0.0",descriptor(),"21.1.251").version());}
    @Test void rejectsWrongLoaderOrMinecraft(){assertThrows(Exception.class,()->RivetRelease.parse("v1.0.0",descriptor(),"21.1.249"));assertThrows(Exception.class,()->RivetRelease.parse("v1.0.0",descriptor(),"21.2.250"));var d=descriptor();d.getAsJsonArray("artifacts").get(0).getAsJsonObject().addProperty("minecraft","1.21.2");assertThrows(Exception.class,()->RivetRelease.parse("v1.0.0",d,"21.1.250"));}
    @Test void rejectsForeignDownloadUrl(){var d=descriptor();d.getAsJsonArray("artifacts").get(0).getAsJsonObject().addProperty("url","https://example.org/rivet.jar");assertThrows(Exception.class,()->RivetRelease.parse("v1.0.0",d,"21.1.250"));}
    @Test void rejectsVersionMismatch(){assertThrows(Exception.class,()->RivetRelease.parse("v1.0.1",descriptor(),"21.1.250"));}
    @Test void rejectsBadHashOrHugeDownload(){var d=descriptor();d.getAsJsonArray("artifacts").get(0).getAsJsonObject().addProperty("sha256","bad");assertThrows(Exception.class,()->RivetRelease.parse("v1.0.0",d,"21.1.250"));var huge=descriptor();huge.getAsJsonArray("artifacts").get(0).getAsJsonObject().addProperty("size",Long.MAX_VALUE);assertThrows(Exception.class,()->RivetRelease.parse("v1.0.0",huge,"21.1.250"));}
    @Test void ignoresPrereleases(){var r=JsonParser.parseString("{\"tag_name\":\"v1.0.0\",\"draft\":false,\"prerelease\":true}").getAsJsonObject();assertThrows(Exception.class,()->RivetRelease.tag(r));}
}
