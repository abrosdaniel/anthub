package dev.abros.anthub.core;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
public final class Diagnostics {
 public static String redact(String text,Path game){
  String home=System.getProperty("user.home","");text=text.replace(game.toString(),"<game>");if(!home.isBlank())text=text.replace(home,"<home>");
  text=text.replaceAll("(?i)(authorization\\s*[:=]\\s*).*","$1<redacted>")
   .replaceAll("(?i)((?:password|passwd|token|secret|api[_-]?key|access[_-]?key|sessionid)\\s*[\\\"']?\\s*[:=]\\s*).*","$1<redacted>")
   .replaceAll("https?://[^\\s<>\\\"]+","<url>")
   .replaceAll("(?i)[A-Z]:[\\\\/][^\\s\\\"<>]+","<path>")
   .replaceAll("/(?:Users|home|root|opt|var|tmp|private|srv)/[^\\s\\\"<>]+","<path>")
   .replaceAll("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]+)?\\b","<ip>")
   .replaceAll("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b","<uuid>");
  return text;
 }
 public static String report(Hub hub,String error)throws IOException{
  StringBuilder text=new StringBuilder("AntHub "+hub.coreVersion()+"\nMinecraft 1.21.1\nNeoForge "+hub.neoVersion()+"\nJava "+System.getProperty("java.version")+"\nOS "+System.getProperty("os.name")+"\n");
  var active=hub.active();text.append("Pack: ").append(active==null?"none":active.version()).append("\nError: ").append(error).append("\n\nAntHub warnings/errors (recent):\n");
  Path log=hub.game.resolve("logs/latest.log");if(Files.isRegularFile(log)&&!Files.isSymbolicLink(log)){
   byte[] bytes;try(SeekableByteChannel input=Files.newByteChannel(log)){long size=input.size();input.position(Math.max(0,size-256*1024));ByteBuffer buffer=ByteBuffer.allocate((int)Math.min(size,256*1024));while(buffer.hasRemaining()&&input.read(buffer)>0){}bytes=Arrays.copyOf(buffer.array(),buffer.position());}
   var lines=new String(bytes,StandardCharsets.UTF_8).lines().filter(line->line.toLowerCase(Locale.ROOT).contains("anthub")&&(line.contains("WARN")||line.contains("ERROR"))).toList();
   for(String line:lines.subList(Math.max(0,lines.size()-100),lines.size()))text.append(line).append('\n');
  }
  return redact(text.toString(),hub.game);
 }
 public static Path save(Path game,String sanitized)throws IOException{Path dir=game.resolve("anthub/diagnostics");if(Files.isSymbolicLink(dir))throw new IOException("Unsafe diagnostics directory");Files.createDirectories(dir);Path file=Files.createTempFile(dir,"anthub-",".txt");Files.writeString(file,sanitized);return file;}
}
