package dev.abros.anthub.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class DatabaseReliabilityTest {
    @TempDir Path identity;
    PgDatabase db;
    @BeforeEach void open() throws Exception { db=TestDatabase.database(identity); }
    private long scalar(String sql) throws Exception {
        return db.transaction(() -> {
            try(var statement=db.connection().createStatement();var row=statement.executeQuery(sql)) {
                row.next();return row.getLong(1);
            }
        });
    }
    @Test void failedTransactionRollsBackDataAndOutboxTogether() throws Exception {
        var store=new CommunityStore(db,CommunityStore.defaults());
        assertThrows(SQLException.class,()->db.transaction(()->{
            store.externalNotice(UUID.randomUUID().toString(),"not committed");
            try(var statement=db.connection().createStatement()){statement.execute("SELECT 1/0");}
            return null;
        }));
        assertEquals(0,scalar("SELECT count(*) FROM notices"));
        assertEquals(0,scalar("SELECT count(*) FROM community_events"));
        assertFalse(db.inTransaction());
    }
    @Test void terminatedConnectionDoesNotPoisonNextTransaction() throws Exception {
        assertThrows(SQLException.class,()->db.transaction(()->{
            try(var statement=db.connection().createStatement()) {
                statement.execute("SELECT pg_terminate_backend(pg_backend_pid())");
            }
            return null;
        }));
        assertFalse(db.inTransaction());
        assertEquals(42,scalar("SELECT 42"));
    }
    @Test void changedMigrationIsRejectedWithoutChangingSchema() throws Exception {
        assertThrows(IllegalStateException.class,()->db.transaction(()->{
            try(var statement=db.connection().createStatement()) {
                statement.executeUpdate("UPDATE schema_versions SET checksum='modified' WHERE version=1");
            }
            DatabaseMigrations.apply(db);return null;
        }));
        db.transaction(()->{DatabaseMigrations.apply(db);return null;});
        assertEquals(2,scalar("SELECT count(*) FROM schema_versions"));
    }
    @Test void newerDatabaseIsRejectedAndTransactionIsCleanedUp() throws Exception {
        assertThrows(IllegalStateException.class,()->db.transaction(()->{
            try(var statement=db.connection().createStatement()) {
                statement.executeUpdate("INSERT INTO schema_versions(version,checksum) VALUES(999,'future')");
            }
            DatabaseMigrations.apply(db);return null;
        }));
        assertEquals(2,scalar("SELECT max(version) FROM schema_versions"));
    }
    @Test void migrationSqlFailureRollsBackEarlierDdl() throws Exception {
        assertThrows(SQLException.class,()->db.transaction(()->{
            try(var statement=db.connection().createStatement()) {
                statement.execute("CREATE TABLE migration_rollback_probe(id INT)");
                // Force the pending migration to encounter its already existing table.
                statement.executeUpdate("DELETE FROM schema_versions WHERE version=2");
            }
            DatabaseMigrations.apply(db);return null;
        }));
        assertEquals(0,scalar("SELECT count(*) FROM pg_tables WHERE schemaname='anthub' AND tablename='migration_rollback_probe'"));
        assertEquals(2,scalar("SELECT count(*) FROM schema_versions"));
        db.transaction(()->{DatabaseMigrations.apply(db);return null;});
    }
    @Test void menuLoadLeavesConnectionsForAuth() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(6);
        var release=new java.util.concurrent.CountDownLatch(1);
        var workers=java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures=new java.util.ArrayList<java.util.concurrent.Future<?>>();
        try {
            for(int i=0;i<6;i++)futures.add(workers.submit(()->{
                try { db.communityTransaction(()->{entered.countDown();release.await(5,java.util.concurrent.TimeUnit.SECONDS);return null;}); }
                catch(Exception failure){throw new RuntimeException(failure);}
            }));
            assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(42,scalar("SELECT 42"));
        } finally { release.countDown();workers.shutdown(); }
        for(var future:futures)future.get(5,java.util.concurrent.TimeUnit.SECONDS);
    }
    @Test void incompleteHistoryAndMissingColumnsFailWithoutRepairingData()throws Exception{
        assertThrows(IllegalStateException.class,()->db.transaction(()->{try(var q=db.connection().createStatement()){q.execute("DELETE FROM schema_versions WHERE version=1");}DatabaseMigrations.apply(db);return null;}));
        assertThrows(SQLException.class,()->db.transaction(()->{try(var q=db.connection().createStatement()){q.execute("ALTER TABLE people RENAME COLUMN name TO broken_name");}DatabaseMigrations.apply(db);return null;}));
        db.transaction(()->{DatabaseMigrations.apply(db);return null;});
    }
}
