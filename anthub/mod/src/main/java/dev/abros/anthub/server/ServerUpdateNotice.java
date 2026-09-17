package dev.abros.anthub.server;

import com.mojang.logging.LogUtils;
import dev.abros.anthub.AntHub;
import dev.abros.anthub.core.CoreUpdater;
import dev.abros.anthub.core.Remote;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Read-only release checks, isolated from ticks, Auth and database workers. */
final class ServerUpdateNotice {
    private static ScheduledExecutorService worker;

    static void install() {
        NeoForge.EVENT_BUS.addListener(ServerUpdateNotice::start);
        NeoForge.EVENT_BUS.addListener(ServerUpdateNotice::stop);
    }

    private static synchronized void start(ServerStartedEvent event) {
        stopWorker();
        var updater = new CoreUpdater(new Remote());
        var announced = new HashSet<String>();
        String running = AntHub.VERSION;
        String minecraft = SharedConstants.getCurrentVersion().getName();
        String neoForge = ModList.get().getModContainerById("neoforge").orElseThrow()
                .getModInfo().getVersion().toString();
        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "AntHub update check");
            thread.setDaemon(true);
            return thread;
        });
        worker.scheduleWithFixedDelay(() -> {
            try {
                var update = updater.check(running, minecraft, neoForge);
                if (!Thread.currentThread().isInterrupted() && update.isPresent()
                        && announced.add(update.get().version())) {
                    LogUtils.getLogger().warn(
                            "AntHub: доступна новая версия {} (установлена {}). Скачать: {}/releases/tag/v{}",
                            update.get().version(), running, CoreUpdater.REPOSITORY, update.get().version());
                    if(!update.get().preservesProtocols())LogUtils.getLogger().warn("AntHub: версия {} меняет сетевые протоколы. Согласуйте обновление сервера и клиентов перед установкой.",update.get().version());
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception failure) {
                LogUtils.getLogger().debug("AntHub: проверка обновлений недоступна; повторим позже", failure);
            }
        }, 0, 6, TimeUnit.HOURS);
    }

    private static synchronized void stop(ServerStoppingEvent event) {
        stopWorker();
    }

    private static void stopWorker() {
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
    }
}
