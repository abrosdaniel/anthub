package dev.abros.anthub.core;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Ordered, checksummed migrations. All pending steps commit together or roll back. */
final class DatabaseMigrations {
 private static final List<String> FILES=List.of("001.sql","002.sql");
 static void apply(PgDatabase db)throws Exception{
  db.lock("anthub-schema");
  try(var statement=db.connection().createStatement()){
   boolean exists;try(var row=statement.executeQuery("SELECT 1 FROM pg_namespace WHERE nspname='anthub'")){exists=row.next();}if(!exists)statement.execute("CREATE SCHEMA anthub");
   statement.execute("CREATE TABLE IF NOT EXISTS schema_versions(version INTEGER PRIMARY KEY,checksum TEXT NOT NULL,applied TIMESTAMPTZ NOT NULL DEFAULT now())");
   try(var row=statement.executeQuery("SELECT coalesce(max(version),0) FROM schema_versions")){row.next();if(row.getInt(1)>FILES.size())throw new IllegalStateException("Database was created by a newer AntHub version");}
   for(int i=0;i<FILES.size();i++){
    String ddl;try(var input=DatabaseMigrations.class.getResourceAsStream("/anthub/migrations/"+FILES.get(i))){ddl=new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8);}String hash=Hashes.sha256(ddl.getBytes(StandardCharsets.UTF_8));
    try(var q=db.connection().prepareStatement("SELECT checksum FROM schema_versions WHERE version=?")){q.setInt(1,i+1);try(var row=q.executeQuery()){if(row.next()){if(!hash.equals(row.getString(1)))throw new IllegalStateException("Applied migration was modified: "+FILES.get(i));continue;}}}
    for(String sql:ddl.split(";"))if(!sql.isBlank())statement.execute(sql);
    try(var q=db.connection().prepareStatement("INSERT INTO schema_versions(version,checksum) VALUES(?,?)")){q.setInt(1,i+1);q.setString(2,hash);q.executeUpdate();}
   }
  }
 }
}
