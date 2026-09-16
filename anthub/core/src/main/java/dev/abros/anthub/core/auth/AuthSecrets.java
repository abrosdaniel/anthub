package dev.abros.anthub.core.auth;

import java.security.*;
import java.util.*;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** Fixed, bounded password work factor; never accept KDF parameters from the network. */
public final class AuthSecrets {
    private static final SecureRandom RANDOM = new SecureRandom();
    private AuthSecrets() {}
    public static byte[] random(int size) { byte[] out=new byte[size]; RANDOM.nextBytes(out); return out; }
    public static String token() { return Base64.getUrlEncoder().withoutPadding().encodeToString(random(32)); }
    public static String digest(String token) { return digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    public static String digest(byte[] value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch(GeneralSecurityException e) { throw new IllegalStateException(e); } }
    public static String password(char[] password) {
        return password(password,6);
    }
    public static String password(char[] password,int minimumLength) {
        if(minimumLength<6||minimumLength>128)throw new IllegalArgumentException("Invalid minimum password length");
        if(password.length<minimumLength||password.length>128)throw new IllegalArgumentException("Пароль должен содержать "+minimumLength+"–128 символов");
        byte[] salt=random(16);return Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(password,salt));
    }
    public static boolean verify(char[] password,String encoded) {
        if(password.length>128)return false;
        String[] parts=encoded.split(":",-1); if(parts.length!=2)return false;
        byte[] salt=Base64.getDecoder().decode(parts[0]),expected=Base64.getDecoder().decode(parts[1]);
        if(salt.length!=16||expected.length!=32)return false;
        byte[] actual=derive(password,salt);try{return MessageDigest.isEqual(expected,actual);}finally{Arrays.fill(actual,(byte)0);}
    }
    private static byte[] derive(char[] password,byte[] salt) {
        var generator=new Argon2BytesGenerator();
        generator.init(new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13).withSalt(salt).withMemoryAsKB(65536).withIterations(3).withParallelism(1).build());
        byte[] hash=new byte[32];generator.generateBytes(password,hash);return hash;
    }
}
