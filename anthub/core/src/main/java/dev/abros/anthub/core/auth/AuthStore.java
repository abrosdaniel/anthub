package dev.abros.anthub.core.auth;

import com.google.gson.*;
import dev.abros.anthub.core.PgDatabase;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Server-wide identity store. All credential revocations and password changes are atomic. */
public final class AuthStore implements AutoCloseable {
    private final PgDatabase database;
    private final String dummy=AuthSecrets.password(AuthSecrets.token().toCharArray());
    public record Account(String name,String uuid,String type,String official,long generation,boolean blocked) {}
    public record Device(String id,String token) {}
    private final int minimumPasswordLength;
    public AuthStore(PgDatabase database){this(database,6);}
    public AuthStore(PgDatabase database,int minimumPasswordLength){if(minimumPasswordLength<6||minimumPasswordLength>128)throw new IllegalArgumentException("Invalid minimum password length");this.database=database;this.minimumPasswordLength=minimumPasswordLength;}
    public int minimumPasswordLength(){return minimumPasswordLength;}
    private Connection connection(){return database.connection();}
    private static final class RejectedCredential extends IllegalArgumentException {
        final String name;final long now;
        RejectedCredential(String name,long now,String message){super(message);this.name=name;this.now=now;}
    }
    private <T>T run(String name,PgDatabase.Work<T> work)throws Exception{
        try{return database.transaction(()->{database.lock("auth:"+name.toLowerCase(Locale.ROOT));return work.run();});}
        catch(RejectedCredential ex){if(database.inTransaction())throw ex;failure(ex.name,ex.now);throw new IllegalArgumentException(ex.getMessage());}
    }
    public static String name(String name){if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Invalid player name");return name;}
    private int update(String sql,Object...args)throws Exception{try(var s=connection().prepareStatement(sql)){bind(s,args);return s.executeUpdate();}}
    private static void bind(PreparedStatement s,Object...args)throws Exception{for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);}
    public Account account(String name)throws Exception{return run(name,()->{
        try(var s=connection().prepareStatement("SELECT name,uuid,type,official,generation,blocked FROM auth_accounts WHERE lower(name)=lower(?)")){s.setString(1,name);try(var r=s.executeQuery()){return r.next()?new Account(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),r.getBoolean(6)):null;}}
    });}
    private String password(String name)throws Exception{try(var s=connection().prepareStatement("SELECT password FROM auth_accounts WHERE lower(name)=lower(?)")){s.setString(1,name);try(var r=s.executeQuery()){return r.next()?r.getString(1):null;}}}
    private interface Work<T>{T run()throws Exception;}
    private <T>T atomic(Work<T> work)throws Exception{return work.run();}
    private void audit(String actor,String action,String target,long now)throws Exception{update("INSERT INTO auth_audit(at,actor,action,target) VALUES(?,?,?,?)",now,actor,action,target);update("DELETE FROM auth_audit WHERE id NOT IN (SELECT id FROM auth_audit ORDER BY id DESC LIMIT 10000)");}
    private static void identity(Account a,String uuid){if(a!=null&&!a.uuid.equals(uuid))throw new IllegalArgumentException("UUID аккаунта изменился. Обратитесь к владельцу сервера");}
    public void reserve(String name,String uuid)throws Exception{run(name,()->{name(name);var a=account(name);if(a==null)update("INSERT INTO auth_accounts(name,uuid,type) VALUES(?,?,'reserved')",name,uuid);return null;});}
    public Account register(String name,String uuid,char[] password,long now)throws Exception{return run(name,()->{
        name(name);if(account(name)!=null)throw new IllegalArgumentException("Для этого имени уже настроен вход. Обратитесь к администратору");
        String hash=AuthSecrets.password(password,minimumPasswordLength);return atomic(()->{update("INSERT INTO auth_accounts(name,uuid,type,password) VALUES(?,?,'local',?)",name,uuid,hash);audit(name,"register",name,now);return account(name);});
    });}
    /** Official proof authenticates only an already linked local account. */
    public Account official(String name,String uuid,String official,long now)throws Exception{return run(name,()->{
        checkRate(name,now);var a=account(name);identity(a,uuid);
        if(a==null||a.blocked||!a.type.equals("local")||a.official==null||!a.official.equals(official))
            throw new RejectedCredential(name,now,"Minecraft-аккаунт не привязан к этому серверному аккаунту. Войдите по паролю");
        update("DELETE FROM auth_failures WHERE lower(name)=lower(?)",name);audit(name,"official-login",name,now);return a;
    });}
    public Account linkOfficial(String name,String uuid,String official,char[] password,long now)throws Exception{return run(name,()->{
        var a=login(name,uuid,password,now);String id=UUID.fromString(official).toString();
        database.lock("official:"+id);
        if(a.official!=null)throw new IllegalArgumentException("Сначала отвяжите текущий Minecraft-аккаунт");
        try(var q=connection().prepareStatement("SELECT 1 FROM auth_accounts WHERE official=?")){q.setString(1,id);try(var rs=q.executeQuery()){if(rs.next())throw new IllegalArgumentException("Minecraft-аккаунт уже привязан к другому аккаунту сервера");}}
        update("UPDATE auth_accounts SET official=?,generation=generation+1 WHERE lower(name)=lower(?)",id,a.name);
        revokeCredentials(a.name);audit(a.name,"official-linked",id,now);return account(a.name);
    });}
    public Account unlinkOfficial(String name,String uuid,char[] password,long now)throws Exception{return run(name,()->{
        var a=login(name,uuid,password,now);if(a.official==null)throw new IllegalArgumentException("Minecraft-аккаунт не привязан");
        update("UPDATE auth_accounts SET official=NULL,generation=generation+1 WHERE lower(name)=lower(?)",a.name);
        revokeCredentials(a.name);audit(a.name,"official-unlinked",a.name,now);return account(a.name);
    });}
    public record Profile(String name,UUID uuid,UUID official){public Profile(String name,UUID uuid){this(name,uuid,null);}}
    public java.util.List<Profile> profiles()throws Exception{return database.transaction(()->{
        var result=new java.util.ArrayList<Profile>();try(var q=connection().createStatement();var rs=q.executeQuery("SELECT name,uuid,official FROM auth_accounts")){while(rs.next())result.add(new Profile(rs.getString(1),UUID.fromString(rs.getString(2)),rs.getString(3)==null?null:UUID.fromString(rs.getString(3))));}return java.util.List.copyOf(result);
    });}
    public void checkRate(String name,long now)throws Exception{run(name,()->{
        try(var s=connection().prepareStatement("SELECT next FROM auth_failures WHERE lower(name)=lower(?)")){s.setString(1,name);try(var r=s.executeQuery()){if(r.next()&&now<r.getLong(1))throw new IllegalArgumentException("Слишком много попыток. Подождите перед повторным входом");}}
    return null;});}
    public void failure(String name,long now)throws Exception{database.transaction(()->{database.lock("auth:"+name.toLowerCase(Locale.ROOT));
        update("DELETE FROM auth_failures WHERE next<?",now-86400000);
        update("DELETE FROM auth_failures WHERE name NOT IN (SELECT name FROM auth_failures ORDER BY next DESC LIMIT 10000)");
        update("INSERT INTO auth_failures VALUES(?,1,?) ON CONFLICT(name) DO UPDATE SET failures=least(auth_failures.failures+1,8),next=?+least(60000,1000*(1 << auth_failures.failures))",name.toLowerCase(Locale.ROOT),now+1000,now);
    return null;});}
    public Account login(String name,String uuid,char[] pass,long now)throws Exception{return run(name,()->{
        checkRate(name,now);var a=account(name);String hash=password(name);
        boolean valid=AuthSecrets.verify(pass,hash==null?dummy:hash);
        if(!valid||a==null||a.blocked||!a.type.equals("local")){throw new RejectedCredential(name,now,"Не удалось войти. Проверьте данные входа");}
        identity(a,uuid);update("DELETE FROM auth_failures WHERE lower(name)=lower(?)",name);return a;
    });}
    public String invite(String actor,String name,long now)throws Exception{return run(name,()->{
        var a=account(name);if(a==null||a.blocked)throw new IllegalArgumentException("Приглашение доступно только для локального незаблокированного аккаунта");
        String token=AuthSecrets.token();return atomic(()->{update("INSERT INTO auth_resets VALUES(?,?,?) ON CONFLICT(name) DO UPDATE SET hash=excluded.hash,expires=excluded.expires",a.name,AuthSecrets.digest(token),now+900000);audit(actor,"reset-invitation",a.name,now);return token;});
    });}
    public void reset(String name,String uuid,String token,char[] pass,long now)throws Exception{run(name,()->{
        checkRate(name,now);var a=account(name);identity(a,uuid);boolean match=false;
        if(token.length()<=128)try(var s=connection().prepareStatement("SELECT hash,expires FROM auth_resets WHERE lower(name)=lower(?)")){s.setString(1,a==null?name:a.name);try(var r=s.executeQuery()){match=r.next()&&r.getLong(2)>now&&MessageEqual(r.getString(1),AuthSecrets.digest(token));}}
        if(!match||a==null||a.blocked){throw new RejectedCredential(name,now,"Приглашение недействительно или истекло");}
        String hash=AuthSecrets.password(pass,minimumPasswordLength);atomic(()->{update("UPDATE auth_accounts SET password=?,type='local',official=NULL,generation=generation+1 WHERE lower(name)=lower(?)",hash,a.name);revokeCredentials(a.name);update("DELETE FROM auth_failures WHERE lower(name)=lower(?)",a.name);audit(a.name,"password-reset",a.name,now);return null;});
    return null;});}
    private static boolean MessageEqual(String a,String b){return java.security.MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.US_ASCII),b.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
    private void revokeCredentials(String name)throws Exception{update("DELETE FROM auth_devices WHERE lower(name)=lower(?)",name);update("DELETE FROM auth_resets WHERE lower(name)=lower(?)",name);}
    public void change(String name,String uuid,char[] oldPass,char[] newPass,long now)throws Exception{run(name,()->{
        var a=login(name,uuid,oldPass,now);String hash=AuthSecrets.password(newPass,minimumPasswordLength);atomic(()->{update("UPDATE auth_accounts SET password=?,generation=generation+1 WHERE lower(name)=lower(?)",hash,a.name);revokeCredentials(a.name);audit(a.name,"password-change",a.name,now);return null;});
    return null;});}
    public Device remember(String name,String label,long now)throws Exception{return run(name,()->{
        var a=account(name);if(a==null||a.blocked||!a.type.equals("local"))throw new IllegalArgumentException("Unavailable");
        if(label.isBlank()||label.length()>48)throw new IllegalArgumentException("Название устройства: 1–48 символов");
        String id=UUID.randomUUID().toString(),token=AuthSecrets.token();return atomic(()->{update("DELETE FROM auth_devices WHERE expires<=?",now);update("INSERT INTO auth_devices VALUES(?,?,?,?,?,?,?)",id,a.name,AuthSecrets.digest(token),label,now,now,now+30L*86400000);update("DELETE FROM auth_devices WHERE lower(name)=lower(?) AND id NOT IN (SELECT id FROM auth_devices WHERE lower(name)=lower(?) ORDER BY created DESC LIMIT 10)",a.name,a.name);audit(a.name,"device-added",id,now);return new Device(id,token);});
    });}
    public Account deviceLogin(String name,String uuid,String token,long now)throws Exception{return run(name,()->{
        checkRate(name,now);var a=account(name);identity(a,uuid);
        if(token.length()>128||a==null||a.blocked||!a.type.equals("local")||update("UPDATE auth_devices SET used=? WHERE lower(name)=lower(?) AND hash=? AND expires>?",now,a.name,AuthSecrets.digest(token),now)==0){throw new RejectedCredential(name,now,"Устройство больше не авторизовано. Введите пароль");}return a;
    });}
    public JsonArray devices(String name,long now)throws Exception{return run(name,()->{var out=new JsonArray();try(var s=connection().prepareStatement("SELECT id,label,created,used,expires FROM auth_devices WHERE lower(name)=lower(?) AND expires>? ORDER BY used DESC")){bind(s,name,now);try(var r=s.executeQuery()){while(r.next()){var j=new JsonObject();j.addProperty("id",r.getString(1));j.addProperty("label",r.getString(2));j.addProperty("created",r.getLong(3));j.addProperty("used",r.getLong(4));j.addProperty("expires",r.getLong(5));out.add(j);}}}return out;});}
    public void revoke(String name,String device,long now)throws Exception{run(name,()->{atomic(()->{if(device.equals("all")){update("DELETE FROM auth_devices WHERE lower(name)=lower(?)",name);update("UPDATE auth_accounts SET generation=generation+1 WHERE lower(name)=lower(?)",name);}else if(update("DELETE FROM auth_devices WHERE lower(name)=lower(?) AND id=?",name,device)!=1)throw new IllegalArgumentException("Устройство не найдено");audit(name,"device-revoked",device,now);return null;});return null;});}
    public boolean hasDevice(String name,String device,long now)throws Exception{return run(name,()->{try(var s=connection().prepareStatement("SELECT 1 FROM auth_devices WHERE lower(name)=lower(?) AND id=? AND expires>?")){bind(s,name,device,now);try(var r=s.executeQuery()){return r.next();}}});}
    public String deviceId(String name,String token)throws Exception{return run(name,()->{try(var s=connection().prepareStatement("SELECT id FROM auth_devices WHERE lower(name)=lower(?) AND hash=?")){bind(s,name,AuthSecrets.digest(token));try(var r=s.executeQuery()){return r.next()?r.getString(1):"";}}});}
    public void block(String actor,String name,boolean blocked,long now)throws Exception{run(name,()->{var a=account(name);if(a==null)throw new IllegalArgumentException("Аккаунт не найден");atomic(()->{update("UPDATE auth_accounts SET blocked=?,generation=generation+1 WHERE lower(name)=lower(?)",blocked,a.name);revokeCredentials(a.name);audit(actor,blocked?"blocked":"unblocked",a.name,now);return null;});return null;});}
    public record SessionKey(String name,long generation,String device){}
    public Set<SessionKey> validSessions(Collection<SessionKey> sessions,long now)throws Exception{return database.transaction(()->{
        var accounts=new HashMap<String,Long>();var devices=new HashMap<String,String>();
        if(sessions.isEmpty())return Set.of();
        try(var q=connection().prepareStatement("SELECT lower(name),generation FROM auth_accounts WHERE NOT blocked AND lower(name)=ANY(?)")){
            q.setArray(1,connection().createArrayOf("text",sessions.stream().map(k->k.name.toLowerCase(Locale.ROOT)).distinct().toArray()));try(var rows=q.executeQuery()){while(rows.next())accounts.put(rows.getString(1),rows.getLong(2));}
        }
        var ids=sessions.stream().map(SessionKey::device).filter(id->!id.isEmpty()).distinct().toArray();
        if(ids.length>0)try(var q=connection().prepareStatement("SELECT id,lower(name) FROM auth_devices WHERE id=ANY(?) AND expires>?")){
            q.setArray(1,connection().createArrayOf("text",ids));q.setLong(2,now);try(var rows=q.executeQuery()){while(rows.next())devices.put(rows.getString(1),rows.getString(2));}
        }
        var valid=new HashSet<SessionKey>();for(var key:sessions){String name=key.name.toLowerCase(Locale.ROOT);if(Objects.equals(accounts.get(name),key.generation)&&(key.device.isEmpty()||name.equals(devices.get(key.device))))valid.add(key);}return valid;
    });}
    @Override public void close()throws Exception{}
}
