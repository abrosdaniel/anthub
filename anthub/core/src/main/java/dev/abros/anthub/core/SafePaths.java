package dev.abros.anthub.core;
import java.nio.file.*;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
public final class SafePaths {
    private static final Set<String> ROOTS=Set.of("mods","config","defaultconfigs","kubejs","resourcepacks","shaderpacks");
    private SafePaths(){}
    public static String key(String s){return Normalizer.normalize(s,Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
    public static void validate(String value){
        if(value==null||value.length()>240||!value.equals(Normalizer.normalize(value,Normalizer.Form.NFC))||value.contains("\\")||value.contains(":")||value.startsWith("/")||value.chars().anyMatch(c->c<32))throw new IllegalArgumentException("Unsafe path: "+value);
        String[] parts=value.split("/",-1);
        if(parts.length<2||!ROOTS.contains(parts[0]))throw new IllegalArgumentException("Protected path: "+value);
        for(String p:parts)if(p.isEmpty()||p.equals(".")||p.equals("..")||p.endsWith(".")||p.endsWith(" ")||p.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?"))throw new IllegalArgumentException("Unsafe segment: "+p);
        if(parts[0].equals("config")&&parts[1].toLowerCase(Locale.ROOT).startsWith("anthub"))throw new IllegalArgumentException("Protected AntHub configuration");
    }
    public static Path resolve(Path root,String value)throws IOException {
        validate(value);Path base=root.toRealPath();Path p=base.resolve(value).normalize();
        if(!p.startsWith(base))throw new IOException("Path escapes game directory");
        for(Path q=p;q!=null&&!q.equals(base);q=q.getParent()){
            if(Files.isSymbolicLink(q))throw new IOException("Symlink rejected: "+q);
            if(Files.exists(q,LinkOption.NOFOLLOW_LINKS)&&!q.toRealPath().startsWith(base))throw new IOException("Redirected path");
        }
        return p;
    }
}
