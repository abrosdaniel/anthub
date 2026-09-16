package dev.abros.anthub.client;
import dev.abros.anthub.core.Remote;
final class Errors {
 static String progress(String message){
  if(message.startsWith("DOWNLOAD|")){String[] p=message.split("\\|",6);if(p.length==6)return Client.tr("download.progress",p[5],String.format(java.util.Locale.ROOT,"%.1f",Long.parseLong(p[1])/1048576.0),String.format(java.util.Locale.ROOT,"%.1f",Long.parseLong(p[2])/1048576.0),Long.parseLong(p[3])/1024,p[4]).getString();}

  if(message.startsWith("Trying mirror "))return Client.tr("download.mirror",message.substring("Trying mirror ".length())).getString();
  return message;
 }
 static String message(Exception error){
  if(error.getCause() instanceof Exception cause && (error instanceof java.util.concurrent.ExecutionException || error instanceof java.util.concurrent.CompletionException))return message(cause);
  if(error.getMessage()!=null && error.getMessage().startsWith("All download sources failed: "))return Client.tr("error.sources",error.getMessage().substring("All download sources failed: ".length())).getString();
  if(error instanceof Remote.HttpFailure http){
   if(http.status==404){String path=http.endpoint.getPath();return Client.tr(path.contains("/channels/")?"error.channelmissing":"error.notfound").getString();}
   if(http.status==403||http.status==429)return Client.tr("error.ratelimit").getString();
  }
  if(error instanceof Remote.Unavailable||error instanceof java.net.UnknownHostException)return Client.tr("error.network").getString();
  if(error instanceof java.nio.file.NoSuchFileException)return Client.tr("error.cachemissing").getString();
  return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
 }
}
