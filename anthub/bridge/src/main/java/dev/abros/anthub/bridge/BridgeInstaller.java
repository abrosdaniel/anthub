package dev.abros.anthub.bridge;
import com.google.gson.*;
import net.neoforged.fml.ModList;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class BridgeInstaller {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ScheduledExecutorService DEADLINES=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Rivet download timeout");t.setDaemon(true);return t;});
    static void prepare(Path directory,Consumer<String> progress)throws Exception{
        Path game=directory.toRealPath();
        String source=System.getProperty("anthub.bundlePath");
        if(source==null)throw new IOException("Переход работает только с JAR, установленным в mods");
        Path old=Path.of(source).toRealPath();
        Path work=MigrationFiles.safe(game,MigrationFiles.WORK);Files.createDirectories(work);
        if(Files.exists(work.resolve("pending")))throw new IOException("Замена уже ожидает закрытия Minecraft");
        var latest=readJson("https://api.github.com/repos/abrosdaniel/rivet/releases/latest");
        String tag=RivetRelease.tag(latest);
        var descriptor=readJson(RivetRelease.REPOSITORY+"/releases/download/"+tag+"/core.json");
        String loader=ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString();
        RivetRelease release=RivetRelease.parse(tag,descriptor,loader);
        MigrationFiles.validateMods(game,old,release.fileName());
        Path staged=MigrationFiles.safe(game,MigrationFiles.WORK+"/rivet.jar");
        if(!Files.isRegularFile(staged)||Files.size(staged)!=release.size()||!MigrationFiles.hash(staged).equals(release.sha256())){
            progress.accept("Загружаем Rivet "+release.version()+"…");
            Path temp=MigrationFiles.safe(game,MigrationFiles.WORK+"/download.tmp");
            try{
                download(release.url(),temp,release.size(),progress);
                if(Files.size(temp)!=release.size()||!MigrationFiles.hash(temp).equals(release.sha256()))throw new IOException("Контрольная сумма Rivet не совпала");
                Files.move(temp,staged,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }finally{Files.deleteIfExists(temp);}
        }
        if(!MigrationFiles.mod(staged,"rivet"))throw new IOException("Загруженный JAR не является Rivet");
        Path helper=MigrationFiles.safe(game,MigrationFiles.WORK+"/helper.jar");
        try(var in=BridgeInstaller.class.getResourceAsStream("/anthub/helper.jar")){
            if(in==null)throw new IOException("Установщик отсутствует в переходном JAR");
            Files.copy(in,helper,StandardCopyOption.REPLACE_EXISTING);
        }
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        Process probe=new ProcessBuilder(java,"-jar",helper.toString(),"--version").redirectErrorStream(true).start();
        if(!probe.waitFor(10,TimeUnit.SECONDS)){probe.destroyForcibly();throw new IOException("Установщик не отвечает");}
        if(probe.exitValue()!=0)throw new IOException("Не удалось запустить установщик");
        Properties plan=new Properties();plan.setProperty("old",old.getFileName().toString());plan.setProperty("oldHash",MigrationFiles.hash(old));
        plan.setProperty("destination",release.fileName());plan.setProperty("version",release.version());plan.setProperty("sha256",release.sha256());plan.setProperty("phase","ready");
        MigrationFiles.write(work.resolve("plan.properties"),plan);
        Path pending=MigrationFiles.safe(game,MigrationFiles.WORK+"/pending");Files.writeString(pending,"Prepared for Rivet "+release.version());
        try{
            var self=ProcessHandle.current();
            Process installer=new ProcessBuilder(java,"-jar",helper.toString(),"apply",game.toString(),Long.toString(self.pid()),self.info().startInstant().orElseThrow().toString())
                .redirectErrorStream(true).redirectOutput(work.resolve("helper.log").toFile()).start();
            Thread.sleep(250);
            if(!installer.isAlive())throw new IOException("Установщик завершился до закрытия Minecraft; смотрите anthub/rivet-migration/helper.log");
        }catch(Exception failure){Files.deleteIfExists(pending);throw failure;}
    }
    private static JsonObject readJson(String url)throws Exception{
        try(var in=response(url)){
            byte[] bytes=in.readNBytes(1024*1024+1);
            if(bytes.length>1024*1024)throw new IOException("Слишком большой манифест Rivet");
            return JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static InputStream response(String url)throws Exception{
        URI uri=URI.create(url);
        for(int redirect=0;redirect<6;redirect++){
            if(!"https".equals(uri.getScheme())||uri.getUserInfo()!=null)throw new IOException("Источник должен использовать HTTPS");
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent","AntHub-Rivet-Migration/3.9.0").GET().build();
            var response=HTTP.send(request,HttpResponse.BodyHandlers.ofInputStream());
            if(response.statusCode()>=300&&response.statusCode()<400){response.body().close();uri=uri.resolve(response.headers().firstValue("Location").orElseThrow());continue;}
            if(response.statusCode()!=200){response.body().close();throw new IOException("GitHub ответил HTTP "+response.statusCode());}
            InputStream body=response.body();
            var timeout=DEADLINES.schedule(()->{try{body.close();}catch(IOException ignored){}},180,TimeUnit.SECONDS);
            return new FilterInputStream(body){@Override public void close()throws IOException{timeout.cancel(false);super.close();}};
        }
        throw new IOException("Слишком много перенаправлений при загрузке Rivet");
    }
    private static void download(String url,Path target,long size,Consumer<String> progress)throws Exception{
        try(var in=response(url);var out=Files.newOutputStream(target)){
            long received=0;byte[] buffer=new byte[65536];int n;
            while((n=in.read(buffer))!=-1){received+=n;if(received>size)throw new IOException("Размер загрузки превышает манифест");out.write(buffer,0,n);
                progress.accept("Загружаем Rivet… "+Math.min(100,received*100/size)+"%");}
        }
    }
}
