package dev.abros.anthub.core;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.*;
import java.util.*;
import java.util.function.Function;

/** Immutable, server-only startup snapshot. Never send or log this configuration. */
public final class ServerSettings {
    private final Map<String,Object> values;
    private ServerSettings(Map<String,Object> values){this.values=Map.copyOf(values);validate();}
    @Override public String toString(){return "AntHub server settings (credentials hidden)";}
    public static String template() throws IOException {
        try(var in=ServerSettings.class.getResourceAsStream("/anthub-server.toml")){
            if(in==null)throw new IOException("Missing AntHub configuration template");
            return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    public static ServerSettings load(Path root)throws IOException {
        Path file=root.resolve("config/anthub-server.toml");
        if(Files.isSymbolicLink(file))throw invalid("файл не должен быть символической ссылкой");
        if(!Files.exists(file)){
            Files.createDirectories(file.getParent());
            try{Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));}
            catch(UnsupportedOperationException unsupported){Files.createFile(file);}
            Files.writeString(file,template());
        }
        if(Files.size(file)>65536)throw invalid("размер файла превышает 64 КиБ");
        return parse(Files.readString(file));
    }
    public static ServerSettings parse(String text){
        Map<String,Object> values,defaults;
        try{values=flatten(new TomlParser().parse(text));defaults=flatten(new TomlParser().parse(template()));}
        catch(Exception failure){throw invalid("ошибка TOML: проверьте кавычки, типы и повторяющиеся параметры (значения скрыты)");}
        // Obsolete punishment defaults are ignored, so existing production configs still load.
        values.remove("moderationVotes.actions.banMinutes");values.remove("moderationVotes.actions.muteMinutes");
        if(!values.keySet().equals(defaults.keySet()))throw invalid("набор разделов или параметров не соответствует шаблону. Старый формат не читается; заполните новый файл по README, раздел «Настройки сервера»");
        for(var e:defaults.entrySet()){
            Object v=values.get(e.getKey()),d=e.getValue();
            if(d instanceof String&&!(v instanceof String)||d instanceof Boolean&&!(v instanceof Boolean)||d instanceof Number&&!(v instanceof Integer||v instanceof Long)||d instanceof List&&!(v instanceof List))throw invalid("неверный тип поля "+e.getKey());
        }
        return new ServerSettings(values);
    }
    private static Map<String,Object> flatten(UnmodifiableConfig config){var out=new HashMap<String,Object>();flatten(config,"",out);return out;}
    private static void flatten(UnmodifiableConfig c,String prefix,Map<String,Object> out){for(var e:c.entrySet()){String key=prefix+e.getKey();Object v=e.getValue();if(v instanceof UnmodifiableConfig child)flatten(child,key+".",out);else out.put(key,v);}}
    public String text(String key){return (String)values.get(key);}
    public boolean flag(String key){return (Boolean)values.get(key);}
    public int number(String key){return Math.toIntExact(((Number)values.get(key)).longValue());}
    private void range(String key,int min,int max){long n=((Number)values.get(key)).longValue();if(n<min||n>max)throw invalid(key+": допустимо "+min+"–"+max);}
    private void validate(){
        skins();range("connection.handshakeTimeoutSeconds",3,60);range("auth.minimumPasswordLength",6,128);
        range("database.port",1,65535);range("database.poolSize",2,32);
        if(!Set.of("false","base","hybrid").contains(text("auth.mode")))throw invalid("auth.mode: ожидается строка false, base или hybrid");
        if(text("menu.helpText").length()>2000)throw invalid("menu.helpText: максимум 2000 символов");
        String env=text("database.passwordEnvironment");if(!env.isEmpty()&&!env.matches("[A-Za-z_][A-Za-z0-9_]*"))throw invalid("database.passwordEnvironment: неверное имя переменной");
        try{votes();community();menu();new DatabaseSettings(text("database.host"),number("database.port"),text("database.database"),text("database.username"),"validation",text("database.sslMode"),text("database.sslRootCert"),number("database.poolSize"));}
        catch(Exception failure){throw invalid("проверьте диапазоны moderationVotes, списки community, ссылки menu и параметры database; значения скрыты");}
    }
    public DatabaseSettings database(){return database(System::getenv);}
    DatabaseSettings database(Function<String,String> environment){
        String env=text("database.passwordEnvironment"),password=env.isEmpty()?text("database.password"):environment.apply(env);
        if(password==null||password.isBlank())throw invalid("заполните database.password или заданную переменную database.passwordEnvironment");
        return new DatabaseSettings(text("database.host"),number("database.port"),text("database.database"),text("database.username"),password,text("database.sslMode"),text("database.sslRootCert"),number("database.poolSize"));
    }
    public dev.abros.anthub.core.skins.SkinSettings skins(){return new dev.abros.anthub.core.skins.SkinSettings(flag("skins.enabled"),number("skins.maxFileSizeMiB"),number("skins.maxSkinsPerPlayer"),text("skins.mojangFallback"));}
    public PlayerStatistics.Settings statistics(){return new PlayerStatistics.Settings(flag("statistics.firstJoin"),flag("statistics.lastActivity"),flag("statistics.totalPlayTime"),flag("statistics.currentSession"),flag("statistics.deaths"));}
    public ModerationVotes.Settings votes(){return new ModerationVotes.Settings(flag("moderationVotes.enabled"),number("moderationVotes.minimumPlayers"),number("moderationVotes.durationSeconds"),number("moderationVotes.minimumPlayMinutes"),number("moderationVotes.initiatorCooldownMinutes"),number("moderationVotes.targetCooldownMinutes"),flag("moderationVotes.actions.kick"),flag("moderationVotes.actions.ban"),flag("moderationVotes.actions.mute"));}
    public JsonObject community(){var j=new JsonObject();j.addProperty("groupsTitle",text("community.groupsTitle"));j.addProperty("maxMemberships",number("community.maxMemberships"));for(String key:List.of("categories","groupTypes","sections")){var a=new JsonArray();for(Object v:(List<?>)values.get("community."+key)){if(!(v instanceof String s))throw invalid("community."+key+": ожидаются строки");a.add(s);}j.add(key,a);}return CommunityStore.validateConfig(j);}
    public JsonObject menu(){var j=new JsonObject();var a=new JsonArray();for(Object v:(List<?>)values.get("menu.links")){if(!(v instanceof UnmodifiableConfig c))throw invalid("menu.links: ожидаются name и url");var link=new JsonObject();for(var e:c.entrySet()){if(!(e.getValue() instanceof String s))throw invalid("menu.links: ожидаются строки");link.addProperty(e.getKey(),s);}a.add(link);}j.add("links",a);return ServerMenuData.validate(j);}
    private static IllegalArgumentException invalid(String detail){return new IllegalArgumentException("AntHub: config/anthub-server.toml — "+detail+". Запуск остановлен.");}
}
