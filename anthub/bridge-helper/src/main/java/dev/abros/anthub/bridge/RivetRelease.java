package dev.abros.anthub.bridge;
import com.google.gson.*;
import java.io.IOException;

public record RivetRelease(String version,String url,String sha256,long size) {
    public static final String REPOSITORY="https://github.com/abrosdaniel/rivet";
    public String fileName(){return "rivet-"+version+"-mc1.21.1-neoforge.jar";}
    public static String tag(JsonObject release) throws IOException {
        if(release.get("draft").getAsBoolean()||release.get("prerelease").getAsBoolean())throw new IOException("Rivet release is not stable");
        String tag=release.get("tag_name").getAsString();
        if(!tag.matches("v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))throw new IOException("Invalid Rivet release tag");
        return tag;
    }
    public static RivetRelease parse(String tag,JsonObject descriptor,String installedLoader) throws IOException {
        if(descriptor.get("schemaVersion").getAsInt()!=1 || !descriptor.get("version").getAsString().equals(tag.substring(1)))
            throw new IOException("Rivet release descriptor mismatch");
        for(var value:descriptor.getAsJsonArray("artifacts")){
            var a=value.getAsJsonObject();
            if(!a.get("minecraft").getAsString().equals("1.21.1") || a.get("java").getAsInt()>Runtime.version().feature() ||
                !loader(installedLoader,a.get("neoForge").getAsString()))continue;
            String version=tag.substring(1),url=a.get("url").getAsString(),hash=a.get("sha256").getAsString();
            long size=a.get("size").getAsLong();
            String expected=REPOSITORY+"/releases/download/"+tag+"/rivet-"+version+"-mc1.21.1-neoforge.jar";
            if(!url.equals(expected)||!hash.matches("[0-9a-f]{64}")||size<1||size>256L*1024*1024)
                throw new IOException("Invalid official Rivet artifact");
            return new RivetRelease(version,url,hash,size);
        }
        throw new IOException("No Rivet release supports this Minecraft and NeoForge version");
    }
    private static boolean loader(String installed,String minimum){
        if(!installed.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!minimum.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))return false;
        String[] a=installed.split("\\."),b=minimum.split("\\.");
        return a[0].equals(b[0])&&a[1].equals(b[1])&&Long.parseLong(a[2])>=Long.parseLong(b[2]);
    }
}
