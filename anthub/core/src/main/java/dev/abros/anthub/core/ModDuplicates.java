package dev.abros.anthub.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
/** Read bounded mod metadata without loading code from local jars. */
public final class ModDuplicates {
 private ModDuplicates(){}
 public static List<String> scan(Path directory)throws IOException{if(!Files.isDirectory(directory))return List.of();var mods=new TreeMap<String,List<String>>();List<Path> files;try(var stream=Files.list(directory)){files=stream.filter(p->p.getFileName().toString().endsWith(".jar")&&!Files.isSymbolicLink(p)).sorted().limit(513).toList();}if(files.size()>512)throw new IOException("В папке mods больше 512 файлов. Проверьте состав сборки.");for(var path:files){try(var jar=new JarFile(path.toFile(),false)){var ids=new HashSet<String>();for(String entry:List.of("META-INF/neoforge.mods.toml","META-INF/mods.toml","fabric.mod.json")){var metadata=jar.getJarEntry(entry);if(metadata==null)continue;String text;try(var in=jar.getInputStream(metadata)){byte[] bytes=in.readNBytes(65537);if(bytes.length>65536)continue;text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);}if(entry.endsWith(".json")){var json=Json.parse(text);if(json.has("id"))ids.add(Json.str(json,"id"));}else{var config=new TomlParser().parse(text);Object list=config.get("mods");if(list instanceof List<?> rows)for(Object row:rows)if(row instanceof UnmodifiableConfig mod){Object id=mod.get("modId");if(id instanceof String value)ids.add(value);}}}for(String id:ids)if(id.matches("[a-z][a-z0-9_\\-]{1,63}"))mods.computeIfAbsent(id,k->new ArrayList<>()).add(path.getFileName().toString());}catch(Exception invalid){/* Integrity audit reports unreadable pack jars separately. */}}return mods.entrySet().stream().filter(e->e.getValue().size()>1).map(e->e.getKey()+": "+String.join(", ",e.getValue())).toList();}
}
