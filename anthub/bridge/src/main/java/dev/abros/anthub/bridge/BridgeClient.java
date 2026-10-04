package dev.abros.anthub.bridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.concurrent.*;

final class BridgeClient {
    static volatile String message="Проверяем последний выпуск Rivet…";
    static volatile boolean ready,failed;
    private static boolean started;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"AntHub Rivet migration");t.setDaemon(true);return t;});
    static void install(){NeoForge.EVENT_BUS.addListener(BridgeClient::opening);}
    private static void opening(ScreenEvent.Opening event){
        if(event.getNewScreen() instanceof TitleScreen){
            event.setNewScreen(new MigrationScreen());
            if(!started){started=true;start();}
        }
    }
    static void start(){
        ready=false;failed=false;message="Проверяем последний выпуск Rivet…";
        IO.submit(()->{
            try{BridgeInstaller.prepare(Minecraft.getInstance().gameDirectory.toPath(),s->message=s);ready=true;
                message="Rivet загружен и проверен. Закройте Minecraft и запустите его снова через лаунчер. Замена завершится после закрытия игры.";
            }catch(Exception failure){failed=true;message="Переход не завершён: "+failure.getMessage()+". AntHub не удалён. Проверьте соединение и повторите попытку.";
                System.getLogger(BridgeClient.class.getName()).log(System.Logger.Level.ERROR,"Rivet migration preparation failed",failure);
            }
        });
    }
}
