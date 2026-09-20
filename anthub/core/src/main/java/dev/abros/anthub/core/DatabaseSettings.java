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
    public static DatabaseSettings load(Path serverRoot)throws Exception { return ServerSettings.load(serverRoot).database(); }
    static DatabaseSettings load(Path serverRoot,java.util.function.Function<String,String> environment)throws Exception { return ServerSettings.load(serverRoot).database(environment); }
}
