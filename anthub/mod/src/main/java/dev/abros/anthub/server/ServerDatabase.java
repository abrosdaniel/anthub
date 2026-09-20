package dev.abros.anthub.server;

import dev.abros.anthub.core.DatabaseSettings;
import dev.abros.anthub.core.PgDatabase;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;

/** One startup attempt per server; database failures abort startup with a safe diagnostic. */
final class ServerDatabase {
    private static PgDatabase database;
    private static boolean attempted;
    private static dev.abros.anthub.core.ServerSettings settings;
    static dev.abros.anthub.core.ServerSettings settings(){if(settings==null)throw new IllegalStateException("AntHub server settings not loaded");return settings;}

    static void install() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
            (net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) -> { reset(); try { settings=dev.abros.anthub.core.ServerSettings.load(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().normalize()); } catch(java.io.IOException error){throw new IllegalStateException("AntHub: cannot read config/anthub-server.toml; startup stopped");} get(); });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
            (net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> reset());
    }

    private static synchronized void reset() {
        if (database != null) database.close();
        database = null;
        attempted = false;
        settings = null;
    }

    static synchronized PgDatabase get() {
        if (!attempted) {
            attempted = true;
            try {
                database = new PgDatabase(settings().database());
            } catch (Exception failure) {
                // Do not print raw driver exceptions or configuration values: they may contain secrets.
                com.mojang.logging.LogUtils.getLogger().error(
                    "AntHub: PostgreSQL не настроена или недоступна. Запуск сервера ОСТАНОВЛЕН. " +
                    "Проверьте config/anthub-server.toml [database], поле password, переменную ANTHUB_DB_PASSWORD (если задана) " +
                    "доступность хоста и порта, TLS и права пользователя на схему anthub. Инструкция: README, раздел PostgreSQL.");
            }
        }
        if(database==null)throw new IllegalStateException("AntHub: запуск сервера остановлен — PostgreSQL не настроена или недоступна. Проверьте config/anthub-server.toml [database], пароль, сеть, TLS и права на схему anthub. См. README, раздел PostgreSQL.");
        return database;
    }
}
