package dev.abros.anthub.core;

import java.nio.file.*;
import java.util.*;

/** Server-only configuration. Never serialize or log credentials. */
public final class DatabaseSettings {
    private final String host,database,username,password,sslMode,sslRootCert;
    private final int port,poolSize;
    public DatabaseSettings(String host,int port,String database,String username,String password,String sslMode,String sslRootCert,int poolSize) {
        if(!host.matches("[A-Za-z0-9._:-]{1,253}")||!database.matches("[A-Za-z0-9_]{1,63}")||!username.matches("[A-Za-z0-9_]{1,63}"))throw new IllegalArgumentException("Invalid database host, database or user name");
        if(port<1||port>65535||poolSize<2||poolSize>32||password.isBlank()||!Set.of("verify-full","disable").contains(sslMode))throw new IllegalArgumentException("Invalid PostgreSQL configuration");
        this.host=host;this.port=port;this.database=database;this.username=username;this.password=password;this.sslMode=sslMode;this.sslRootCert=sslRootCert;this.poolSize=poolSize;
    }
    public String jdbcUrl(){return "jdbc:postgresql://"+(host.contains(":")?"["+host+"]":host)+":"+port+"/"+database;}
    public String username(){return username;}public String password(){return password;}public String sslMode(){return sslMode;}public String sslRootCert(){return sslRootCert;}public int poolSize(){return poolSize;}
    @Override public String toString(){return "AntHub PostgreSQL configuration (credentials hidden)";}
    public static DatabaseSettings load(Path serverRoot)throws Exception {
        return load(serverRoot,System::getenv);
    }
    static DatabaseSettings load(Path serverRoot,java.util.function.Function<String,String> environment)throws Exception {
        Path file=serverRoot.resolve("config/anthub-database.properties");
        if(Files.isSymbolicLink(file))throw new IllegalArgumentException("Unsafe database configuration path");
        if(!Files.exists(file)){
            Files.createDirectories(file.getParent());
            Files.writeString(file,"host=127.0.0.1\nport=5432\ndatabase=anthub\nusername=anthub\n# PostgreSQL user password. ANTHUB_DB_PASSWORD overrides this value when set.\npassword=\npoolSize=8\nsslMode=verify-full\nsslRootCert=\npasswordEnvironment=ANTHUB_DB_PASSWORD\npasswordFile=config/anthub-db.password\n",StandardOpenOption.CREATE_NEW);
        }
        var p=new Properties();try(var reader=Files.newBufferedReader(file)){p.load(reader);}
        for(String key:p.stringPropertyNames())if(!Set.of("host","port","database","username","password","poolSize","sslMode","sslRootCert","passwordEnvironment","passwordFile").contains(key))throw new IllegalArgumentException("Unknown database setting: "+key);
        String password=environment.apply(p.getProperty("passwordEnvironment","ANTHUB_DB_PASSWORD"));
        if(password==null||password.isBlank())password=p.getProperty("password","");
        if(password==null||password.isBlank()){
            Path secret=serverRoot.resolve(p.getProperty("passwordFile","config/anthub-db.password")).normalize();
            if(!secret.startsWith(serverRoot.normalize())||Files.isSymbolicLink(secret)||!Files.isRegularFile(secret)||Files.size(secret)>4096)throw new IllegalStateException("Configure config/anthub-database.properties and set password, ANTHUB_DB_PASSWORD or the password file before starting AntHub server.");
            password=Files.readString(secret).stripTrailing();
        }
        return new DatabaseSettings(p.getProperty("host","127.0.0.1"),Integer.parseInt(p.getProperty("port","5432")),p.getProperty("database","anthub"),p.getProperty("username","anthub"),password,p.getProperty("sslMode","verify-full"),p.getProperty("sslRootCert",""),Integer.parseInt(p.getProperty("poolSize","8")));
    }
}
