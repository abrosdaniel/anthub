package dev.abros.anthub.server;

import dev.abros.anthub.core.DatabaseSettings;
import dev.abros.anthub.core.PgDatabase;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;

/** One startup attempt per server; database failures abort startup with a safe diagnostic. */
final class ServerDatabase {
    private static PgDatabase database;
    private static boolean attempted;

    static void install() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
            (net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) -> { reset(); get(); });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
            (net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> reset());
    }

    private static synchronized void reset() {
        if (database != null) database.close();
        database = null;
        attempted = false;
    }

    static synchronized PgDatabase get() {
        if (!attempted) {
            attempted = true;
            try {
                database = new PgDatabase(DatabaseSettings.load(
                    net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().normalize()));
            } catch (Exception failure) {
                // Do not print raw driver exceptions or configuration values: they may contain secrets.
                com.mojang.logging.LogUtils.getLogger().error(
                    "AntHub: PostgreSQL не настроена или недоступна. Запуск сервера ОСТАНОВЛЕН. " +
                    "Проверьте config/anthub-database.properties, поле password, переменную ANTHUB_DB_PASSWORD (если задана) или файл пароля, " +
                    "доступность хоста и порта, TLS и права пользователя на схему anthub. Инструкция: README, раздел PostgreSQL.");
            }
        }
        if(database==null)throw new IllegalStateException("AntHub: запуск сервера остановлен — PostgreSQL не настроена или недоступна. Проверьте config/anthub-database.properties, пароль, сеть, TLS и права на схему anthub. См. README, раздел PostgreSQL.");
        return database;
    }
}
