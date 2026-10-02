package dev.abros.anthub.core;
import com.google.gson.*;
/** People read repository, kept separate from community commands and permissions. */
final class CommunityPeople {
 private final PgDatabase database;CommunityPeople(PgDatabase database){this.database=database;}private java.sql.Connection connection(){return database.connection();}
    JsonArray people(String query,int page)throws Exception{return database.communityTransaction(()->{var out=new JsonArray();try(var s=connection().prepareStatement("SELECT id,name,seen,role FROM people WHERE strpos(lower(name),lower(?))>0 ORDER BY name LIMIT 20 OFFSET ?")){s.setString(1,query);s.setInt(2,page*20);try(var rows=s.executeQuery()){while(rows.next()){var j=new JsonObject();j.addProperty("uuid",rows.getString(1));j.addProperty("name",rows.getString(2));j.addProperty("seen",rows.getLong(3));j.addProperty("role",rows.getString(4));out.add(j);}}}return out;});}
    JsonObject peopleCursor(String query,String cursor)throws Exception{return database.communityTransaction(()->{String scope="people:"+query,after=PlayerCursor.after(cursor,scope);var rows=new JsonArray();try(var q=connection().prepareStatement("SELECT id,name,seen FROM people WHERE strpos(lower(name),lower(?))>0 AND id>? ORDER BY id COLLATE \"C\" LIMIT 21")){q.setString(1,query);q.setString(2,after);try(var rs=q.executeQuery()){while(rs.next()){var j=new JsonObject();j.addProperty("uuid",rs.getString(1));j.addProperty("name",rs.getString(2));j.addProperty("seen",rs.getLong(3));rows.add(j);}}}return PlayerCursor.page(rows,scope);});}
}
