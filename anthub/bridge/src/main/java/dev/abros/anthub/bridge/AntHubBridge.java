package dev.abros.anthub.bridge;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;

@Mod("anthub")
public final class AntHubBridge {
    public AntHubBridge(){
        if(FMLEnvironment.dist!=Dist.CLIENT)
            throw new IllegalStateException("AntHub 3.9.0 migrates clients only. Stop this server and migrate to Rivet manually: preserve the config and auth identity, rename the PostgreSQL schema to rivet, and install Rivet instead of AntHub. The database and user names do not need to change.");
        BridgeClient.install();
    }
}
