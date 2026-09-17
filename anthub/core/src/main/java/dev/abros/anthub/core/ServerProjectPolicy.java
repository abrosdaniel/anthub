package dev.abros.anthub.core;

import com.google.gson.JsonObject;
import java.time.Instant;

/** Requirements captured once at server startup, never changed by a player connecting. */
public record ServerProjectPolicy(String repository, boolean required, RepositoryClient.Release release,
                                  String checkedAt, String problem, String hash, String digest) {
    public static ServerProjectPolicy load(RepositoryClient client,String project,boolean required)throws Exception {
        String checked=Instant.now().toString();
        if(project.isBlank()) {
            if(required)throw new IllegalArgumentException("AntHub: requireProjectPack=true требует ссылку project в config/anthub-server.toml");
            return new ServerProjectPolicy("",false,null,checked,"","","");
        }
        String repository=Repositories.normalize(project);
        try {
            var release=client.fetchOrCached(repository);
            String hash=release.hash(),digest=PackProof.digest(release.manifest(),null);
            // Configuring this repository is the server owner's explicit trust decision.
            if(!release.offline())client.trust(release);
            return new ServerProjectPolicy(repository,required,release,checked,"",hash,digest);
        } catch(Exception failure) {
            if(failure instanceof InterruptedException){Thread.currentThread().interrupt();throw failure;}
            if(required)throw new IllegalStateException("AntHub: не удалось загрузить проверенный снимок проекта "+repository+". Запуск остановлен. Проверьте доступ к GitHub, опубликованный релиз pack-vA.B.C и его манифест. Причина: "+failure.getMessage(),failure);
            return new ServerProjectPolicy(repository,false,null,checked,"Не удалось загрузить проект: "+failure.getMessage(),"","");
        }
    }
    public String version(){return release==null?"":release.manifest().version();}
    public String requiredAntHubVersion(){return release==null?"":release.manifest().anthubVersion();}
    public String serverId(){return release==null?"":release.manifest().servers().getFirst().id();}
    public boolean offline(){return release!=null&&release.offline();}
    public String verify(JsonObject state) {
        if(!required)return "";
        if(release==null)return "AntHub: POLICY_ERROR";
        try {
            if(!repository.equals(Repositories.normalize(Json.str(state,"repository")))||!hash.equals(Json.str(state,"lockSha256")))
                return "AntHub: сервер требует сборку "+version()+". Откройте AntHub для обновления.";
            if(state.get("protocolVersion").getAsInt()!=WireProtocols.version("pack")||!Versions.supportsBranch(Json.str(state,"coreVersion"),requiredAntHubVersion()))
                return "Для этого проекта нужна ветка AntHub "+requiredAntHubVersion()+". Выберите версию на главной.";
            if(!digest.equals(Json.opt(state,"requiredFilesDigest","")))return "AntHub: REPAIR_REQUIRED";
            return "";
        } catch(RuntimeException malformed) { return "AntHub: invalid client pack response"; }
    }
}
