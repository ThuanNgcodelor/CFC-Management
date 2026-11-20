package com.booking.system.health.importer.history;

import com.booking.system.health.importer.baseline.HealthBaselineData.Dataset;
import com.booking.system.health.importer.HealthImportSupport;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** The shipped bundle contains ciphertext only. Its key must never be packaged with the application. */
public final class HealthHistoryCrypto {
    public static final int MAX_BYTES=32*1024*1024;
    private static final byte[] MAGIC="CFC-HH1\n".getBytes(StandardCharsets.US_ASCII);
    private static final int NONCE_BYTES=12;
    private HealthHistoryCrypto() { }
    public record Package(int version,boolean sidYearsConfirmed,Dataset dataset) { }
    public static byte[] seal(Package data,byte[] key) throws IOException,GeneralSecurityException {
        byte[] json=HealthImportSupport.json(data).getBytes(StandardCharsets.UTF_8);
        if(json.length>MAX_BYTES)throw new IOException("History package exceeds limit");
        var zipped=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(zipped)){gzip.write(json);}
        var nonce=new byte[NONCE_BYTES];new SecureRandom().nextBytes(nonce);
        var cipher=cipher(Cipher.ENCRYPT_MODE,key,nonce);
        var out=new ByteArrayOutputStream();out.write(MAGIC);out.write(nonce);out.write(cipher.doFinal(zipped.toByteArray()));return out.toByteArray();
    }
    public static Package open(byte[] bytes,byte[] key) throws IOException,GeneralSecurityException {
        if(bytes.length>MAX_BYTES||bytes.length<MAGIC.length+NONCE_BYTES+16||!Arrays.equals(MAGIC,Arrays.copyOf(bytes,MAGIC.length)))throw new IOException("Invalid history package");
        byte[] nonce=Arrays.copyOfRange(bytes,MAGIC.length,MAGIC.length+NONCE_BYTES);
        byte[] compressed=cipher(Cipher.DECRYPT_MODE,key,nonce).doFinal(bytes,MAGIC.length+NONCE_BYTES,bytes.length-MAGIC.length-NONCE_BYTES);
        try(var zip=new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] json=zip.readNBytes(MAX_BYTES+1);if(json.length>MAX_BYTES)throw new IOException("History package exceeds limit");
            var result=HealthImportSupport.read(new String(json,StandardCharsets.UTF_8),Package.class);
            if(result.version()!=1||result.dataset()==null||result.dataset().sources().isEmpty()||result.dataset().sources().size()>10)throw new IOException("Unsupported history package");
            return result;
        } finally {Arrays.fill(compressed,(byte)0);}
    }
    private static Cipher cipher(int mode,byte[] key,byte[] nonce) throws GeneralSecurityException {
        if(key==null||key.length!=32)throw new InvalidKeyException("History key must be 32 bytes");
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD(MAGIC);return cipher;
    }
}
