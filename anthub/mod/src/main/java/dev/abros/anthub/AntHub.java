package dev.abros.anthub;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import dev.abros.anthub.server.ServerIntegration;
import dev.abros.anthub.network.Protocol;
@Mod("anthub")
public final class AntHub {
    public static String VERSION="unknown";
    public AntHub(IEventBus bus,ModContainer container){
        VERSION=container.getModInfo().getVersion().toString();
        bus.addListener(Protocol::register);
        bus.addListener(dev.abros.anthub.network.SkinWire::register);
        bus.addListener(dev.abros.anthub.server.AuthProtocol::register);
        if(FMLEnvironment.dist==Dist.CLIENT)dev.abros.anthub.client.Client.install(bus);
        else ServerIntegration.install(bus,container);
    }
}
