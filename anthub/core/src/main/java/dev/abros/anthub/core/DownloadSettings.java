package dev.abros.anthub.core;
/** Client download preferences. Hash verification remains mandatory for every source. */
public record DownloadSettings(int parallel,int limitMiB,String mirror){
 public DownloadSettings{if(parallel<1||parallel>4||limitMiB<0||limitMiB>100||!java.util.Set.of("auto","modrinth","curseforge").contains(mirror))throw new IllegalArgumentException("Некорректные настройки загрузки");}
 public static DownloadSettings defaults(){return new DownloadSettings(4,0,"auto");}
 public boolean preferred(String url){String host=Remote.https(url).getHost();return mirror.equals("modrinth")&&host.equals("cdn.modrinth.com")||mirror.equals("curseforge")&&(host.equals("edge.forgecdn.net")||host.equals("mediafilez.forgecdn.net"));}
}
