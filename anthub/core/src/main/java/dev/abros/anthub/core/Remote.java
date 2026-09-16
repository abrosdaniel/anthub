package dev.abros.anthub.core;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongConsumer;
public class Remote {
 public static class Unavailable extends IOException{public Unavailable(String message,Throwable cause){super(message,cause);}public Unavailable(String message){super(message);}}
 public static final class HttpFailure extends Unavailable {
  public final int status;public final URI endpoint;
  public HttpFailure(int status,URI endpoint){super("HTTP "+status+" from "+endpoint.getHost());this.status=status;this.endpoint=endpoint;}
 }
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
 private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"AntHub network timeout");t.setDaemon(true);return t;});
 private Path metadataCache;
 public void cacheMetadata(Path directory)throws IOException{Files.createDirectories(directory);metadataCache=directory;}
 public static URI https(String s){URI u=URI.create(s);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null)throw new IllegalArgumentException("HTTPS required");return u;}
 private static void publicAddress(URI u)throws IOException{for(InetAddress a:InetAddress.getAllByName(u.getHost()))if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress()||(a.getAddress().length==16&&(a.getAddress()[0]&0xfe)==0xfc))throw new IOException("Private metadata endpoint rejected");}
 public byte[] bytes(String url,int limit)throws IOException,InterruptedException{
  Path cache=metadataCache==null?null:metadataCache.resolve(Hashes.sha256(url.getBytes(java.nio.charset.StandardCharsets.UTF_8)));Path validators=cache==null?null:cache.resolveSibling(cache.getFileName()+".json");
  String etag="";if(validators!=null&&Files.exists(validators))etag=Json.opt(Json.read(validators),"etag","");
  URI u=https(url);
  for(int redirect=0;redirect<=5;redirect++){
   publicAddress(u);var builder=HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(30)).header("User-Agent","AntHub/1").header("Cache-Control","no-cache");if(!etag.isEmpty()&&cache!=null&&Files.exists(cache))builder.header("If-None-Match",etag);
   HttpResponse<InputStream> response;
   try{response=client.send(builder.GET().build(),HttpResponse.BodyHandlers.ofInputStream());}catch(IOException e){throw new Unavailable("Metadata unavailable",e);}
   try(var in=response.body()){
    int code=response.statusCode();if(code==304&&cache!=null&&Files.exists(cache)){byte[] b=Files.readAllBytes(cache);if(b.length>limit)throw new IOException("Metadata limit");return b;}
    if(code>=300&&code<400){u=https(u.resolve(response.headers().firstValue("Location").orElseThrow()).toString());etag="";continue;}
    if(code!=200)throw new HttpFailure(code,u);
    ByteArrayOutputStream out=new ByteArrayOutputStream();copy(in,out,limit,new AtomicBoolean(),n->{});byte[] bytes=out.toByteArray();
    if(cache!=null){Path tmp=Files.createTempFile(metadataCache,"metadata-",".tmp");try{Files.write(tmp,bytes);Json.move(tmp,cache);Json.write(validators,java.util.Map.of("etag",response.headers().firstValue("ETag").orElse("")));}finally{Files.deleteIfExists(tmp);}}
    return bytes;
   }
  }throw new IOException("Too many redirects");
 }
 public void download(String url,Path to,long limit,AtomicBoolean cancel)throws IOException,InterruptedException{download(url,to,limit,cancel,n->{});}
 public void download(String url,Path to,long limit,AtomicBoolean cancel,LongConsumer progress)throws IOException,InterruptedException{
  IOException failure=null;
  for(int attempt=0;attempt<3;attempt++){
   if(cancel.get())throw new IOException("Cancelled");
   try{transfer(url,to,limit,cancel,progress);return;}catch(Unavailable e){failure=e;if(attempt<2){for(int n=0;n<(1<<attempt)*10;n++){if(cancel.get())throw new IOException("Cancelled");Thread.sleep(100);}}}
  }throw failure;
 }
 private void transfer(String url,Path to,long limit,AtomicBoolean cancel,LongConsumer progress)throws IOException,InterruptedException{
  Path validator=to.resolveSibling(to.getFileName()+".etag");long offset=Files.exists(to)?Files.size(to):0;String etag=Files.exists(validator)?Files.readString(validator):"";
  if(etag.isBlank()||etag.startsWith("W/")||offset>limit){offset=0;Files.deleteIfExists(to);}
  URI u=https(url);
  for(int redirects=0;redirects<=5;redirects++){
   publicAddress(u);var builder=HttpRequest.newBuilder(u).timeout(Duration.ofSeconds(30)).header("User-Agent","AntHub/1");if(offset>0)builder.header("Range","bytes="+offset+"-").header("If-Range",etag);
   HttpResponse<InputStream> response;try{response=client.send(builder.GET().build(),HttpResponse.BodyHandlers.ofInputStream());}catch(IOException e){throw new Unavailable("Download unavailable",e);}
   try(var in=response.body()){
    int code=response.statusCode();if(code>=300&&code<400){u=https(u.resolve(response.headers().firstValue("Location").orElseThrow()).toString());continue;}
    if(code==429||code==503){long seconds=1;try{seconds=Long.parseLong(response.headers().firstValue("Retry-After").orElse("1"));}catch(NumberFormatException ignored){}for(int n=0;n<Math.min(60,seconds)*10;n++){if(cancel.get())throw new IOException("Cancelled");Thread.sleep(100);}throw new Unavailable("HTTP "+code);}
    if(code!=200&&code!=206)throw new HttpFailure(code,u);
    if(code==206){String range=response.headers().firstValue("Content-Range").orElse("");if(offset<=0||!range.startsWith("bytes "+offset+"-")||!range.endsWith("/"+limit))throw new IOException("Invalid download range");}else offset=0;
    Files.writeString(validator,response.headers().firstValue("ETag").orElse(""));
    try(OutputStream out=Files.newOutputStream(to,StandardOpenOption.CREATE,StandardOpenOption.WRITE,offset>0?StandardOpenOption.APPEND:StandardOpenOption.TRUNCATE_EXISTING)){
     try{copy(in,out,limit-offset,cancel,progress);}catch(IOException e){if(cancel.get())throw e;throw new Unavailable("Download interrupted",e);}
    }return;
   }
  }throw new IOException("Too many redirects");
 }
 private static void copy(InputStream in,OutputStream out,long limit,AtomicBoolean cancel,LongConsumer progress)throws IOException{
  Thread owner=Thread.currentThread();AtomicLong touched=new AtomicLong(System.nanoTime());AtomicBoolean timeout=new AtomicBoolean();
  var timer=TIMER.scheduleAtFixedRate(()->{if(cancel.get()||owner.isInterrupted()||System.nanoTime()-touched.get()>TimeUnit.SECONDS.toNanos(30)){timeout.set(true);try{in.close();}catch(IOException ignored){}}},1,1,TimeUnit.SECONDS);
  try{byte[] b=new byte[65536];long total=0;for(int n;(n=in.read(b))!=-1;){if(cancel.get())throw new IOException("Cancelled");touched.set(System.nanoTime());total+=n;if(total>limit)throw new IOException("Download exceeds declared size");out.write(b,0,n);progress.accept(n);}if(timeout.get())throw new IOException("Download idle timeout");}finally{timer.cancel(false);}
 }
}
