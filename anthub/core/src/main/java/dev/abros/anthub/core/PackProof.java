package dev.abros.anthub.core;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
/** Consistency check for required client files; not an anti-cheat attestation. */
public final class PackProof {
 public static String digest(Manifest manifest,Path game)throws IOException{
  Set<String> required=new Selection(manifest).resolve(Set.of());var lines=new TreeMap<String,String>();
  for(var f:manifest.files())if(required.contains(f.componentId())&&f.policy().equals("enforce")){
   String hash=game==null?f.sha256():Planner.hash(SafePaths.resolve(game,f.path()));if(hash==null)hash="missing";lines.put(f.path(),hash);
  }
  StringBuilder bytes=new StringBuilder();lines.forEach((path,hash)->bytes.append(path).append('\0').append(hash).append('\n'));
  return Hashes.sha256(bytes.toString().getBytes(StandardCharsets.UTF_8));
 }
}
