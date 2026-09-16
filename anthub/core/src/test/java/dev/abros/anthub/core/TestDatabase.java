package dev.abros.anthub.core;
import java.nio.file.Path;
/** Shared local test database; each test's temporary identity clears its own test run. */
public final class TestDatabase {
 private static PgDatabase database;private static Path test;
 public static synchronized PgDatabase database(Path identity)throws Exception{
  if(database==null){String password=System.getenv("ANTHUB_TEST_DB_PASSWORD");if(password==null)throw new IllegalStateException("PostgreSQL tests require ANTHUB_TEST_DB_PASSWORD and an isolated test database");database=new PgDatabase(new DatabaseSettings(System.getenv().getOrDefault("ANTHUB_TEST_DB_HOST","127.0.0.1"),Integer.parseInt(System.getenv().getOrDefault("ANTHUB_TEST_DB_PORT","55439")),"anthub_test","anthub_test",password,"disable","",8));Runtime.getRuntime().addShutdownHook(new Thread(()->database.close()));}
  if(!identity.equals(test)){database.transaction(()->{try(var statement=database.connection().createStatement()){statement.execute("TRUNCATE documents,people,notices,records,preferences,auth_devices,auth_resets,auth_accounts,auth_failures,auth_audit RESTART IDENTITY CASCADE");}return null;});test=identity;}
  return database;
 }
}
