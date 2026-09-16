package dev.abros.anthub.core;
import java.net.URI;
import java.util.Locale;
public final class Repositories {
    private Repositories(){}
    public static String normalize(String input){
        URI u=URI.create(input.trim());
        if(!"https".equals(u.getScheme())||!"github.com".equalsIgnoreCase(u.getHost())||u.getPort()!=-1||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null)throw new IllegalArgumentException("Expected public GitHub repository URL");
        String p=u.getPath().replaceFirst("/$","").replaceFirst("\\.git$","");
        if(!p.matches("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")||p.contains("/../"))throw new IllegalArgumentException("Expected owner/repository");
        return "https://github.com"+p.toLowerCase(Locale.ROOT);
    }
    public static String raw(String repo,String path){return "https://raw.githubusercontent.com/"+normalize(repo).substring("https://github.com/".length())+"/HEAD/"+path;}
}
