package com.booking.system.infrastructure.service;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RouterCredentialCipher {
    public static class CredentialException extends IllegalStateException {
        public CredentialException(String message) { super(message); }
    }
    private final Path keyFile;
    private final SecureRandom random = new SecureRandom();

    public RouterCredentialCipher(@Value("${cfc.infrastructure.credential-key-file:${user.home}/.local/share/cfcbase/infrastructure/credential.key}") String keyFile) {
        this.keyFile = Path.of(keyFile);
    }

    public String encrypt(String value) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(true), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD("cfc-router-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] body = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + body.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(body, 0, payload, nonce.length, body.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new CredentialException("Không thể mã hóa mật khẩu; kiểm tra quyền tệp khóa trên server.");
        }
    }

    public String decrypt(String value) {
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            if (payload.length < 29) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(false), "AES"),
                    new GCMParameterSpec(128, Arrays.copyOf(payload, 12)));
            cipher.updateAAD("cfc-router-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new String(cipher.doFinal(payload, 12, payload.length - 12), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new CredentialException("Không đọc được mật khẩu đã lưu; khôi phục tệp khóa hoặc nhập lại mật khẩu MikroTik.");
        }
    }

    private synchronized byte[] key(boolean create) throws java.io.IOException {
        if (!Files.exists(keyFile) && create) {
            Files.createDirectories(keyFile.toAbsolutePath().getParent(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            try (var channel = Files.newByteChannel(keyFile, java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
            }
        }
        byte[] bytes = Files.readAllBytes(keyFile);
        if (bytes.length != 32) throw new java.io.IOException("Invalid credential key");
        return bytes;
    }
}
