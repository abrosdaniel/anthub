package dev.abros.anthub.core;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
public final class Hashes {
    private Hashes(){}
    public static String sha256(byte[] b){return HexFormat.of().formatHex(digest().digest(b));}
    public static String sha256(Path p)throws IOException {MessageDigest d=digest();try(InputStream in=Files.newInputStream(p)){byte[] b=new byte[65536];for(int n;(n=in.read(b))!=-1;)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    public static void check(String hash){if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid SHA-256");}
}
