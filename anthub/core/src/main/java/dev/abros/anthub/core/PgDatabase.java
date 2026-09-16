package dev.abros.anthub.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.Objects;

/** One bounded pool per server, with an explicit transaction scope per operation. */
public final class PgDatabase implements AutoCloseable {
    @FunctionalInterface public interface Work<T> { T run() throws Exception; }
    private final HikariDataSource pool;
    private final ThreadLocal<Connection> current = new ThreadLocal<>();

    public PgDatabase(DatabaseSettings settings) throws Exception {
        var config = new HikariConfig();
        config.setPoolName("AntHub database");
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(settings.poolSize());
        config.setMinimumIdle(2);
        config.setConnectionTimeout(3000);
        config.setValidationTimeout(1000);
        config.setInitializationFailTimeout(5000);
        config.addDataSourceProperty("connectTimeout", "5");
        config.addDataSourceProperty("socketTimeout", "15");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("sslmode", settings.sslMode());
        if (!settings.sslRootCert().isEmpty()) config.addDataSourceProperty("sslrootcert", settings.sslRootCert());
        config.addDataSourceProperty("ApplicationName", "AntHub");
        config.addDataSourceProperty("currentSchema", "anthub");
        config.addDataSourceProperty("options", "-c statement_timeout=5000 -c lock_timeout=2000 -c idle_in_transaction_session_timeout=15000");
        // PostgreSQL resolves immutable schema/table names; all user values use parameters.
        HikariDataSource opened;
        try { opened = new HikariDataSource(config); }
        catch (RuntimeException ex) { throw new IllegalStateException("Cannot connect to AntHub PostgreSQL. Check database settings, credentials and connectivity."); }
        pool = opened;
        try {
            transaction(() -> {
                lock("schema-v1");
                try (var statement = connection().createStatement()) {
                    boolean schemaExists;
                    try(var schemas=statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname='anthub'")){schemaExists=schemas.next();}
                    if(!schemaExists)statement.execute("CREATE SCHEMA anthub");
                    try (var input = PgDatabase.class.getResourceAsStream("/anthub/database.sql")) {
                        String ddl = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
                        for (String sql : ddl.split(";")) if (!sql.isBlank()) statement.execute(sql);
                    }
                }
                return null;
            });
        } catch (Exception ex) { pool.close(); throw new IllegalStateException("Cannot initialize AntHub PostgreSQL schema. Check database ownership and PostgreSQL version. SQL state: "+(ex instanceof SQLException sql?sql.getSQLState():"unavailable")); }
    }

    public boolean inTransaction(){return current.get()!=null;}

    public Connection connection() {
        var connection = current.get();
        if (connection == null) throw new IllegalStateException("Database access outside transaction");
        return connection;
    }

    public <T> T transaction(Work<T> work) throws Exception {
        if (current.get() != null) return work.run();
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            current.set(connection);
            try { T result = work.run(); connection.commit(); return result; }
            catch (Exception | Error ex) { try { connection.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); } throw ex; }
            finally { current.remove(); }
        }
    }

    /** Transaction-scoped lock also protects a key whose row does not exist yet. */
    public void lock(String key) throws SQLException {
        long value;
        try { value = java.nio.ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))).getLong(); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        try (var statement = connection().prepareStatement("SELECT pg_advisory_xact_lock(?)")) { statement.setLong(1, value); statement.execute(); }
    }

    public int activeConnections() { return pool.getHikariPoolMXBean().getActiveConnections(); }
    @Override public void close() { pool.close(); }
}
