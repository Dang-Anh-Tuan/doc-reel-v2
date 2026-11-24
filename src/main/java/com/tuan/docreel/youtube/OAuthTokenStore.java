package com.tuan.docreel.youtube;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Lưu refresh token trên đĩa, mã hóa AES-256-GCM (khóa = SHA-256 của passphrase cấu hình). File = base64(iv ‖
 * ciphertext). Sai khóa / file bị sửa → GCM từ chối, trả {@link Optional#empty()}.
 */
@Component
public class OAuthTokenStore {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Path file;
    private final String passphrase;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public OAuthTokenStore(YouTubeProperties props) {
        this(props.tokenFile(), props.encryptionKey());
    }

    public OAuthTokenStore(Path file, String passphrase) {
        this.file = file;
        this.passphrase = passphrase;
    }

    public void save(String refreshToken) {
        byte[] key = key();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, Base64.getEncoder().encodeToString(out));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không mã hóa được refresh token", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<String> load() {
        if (!Files.isRegularFile(file) || passphrase == null || passphrase.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] all = Base64.getDecoder().decode(Files.readString(file).trim());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key(), "AES"),
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(all, 0, IV_BYTES)));
            return Optional.of(
                    new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException | IndexOutOfBoundsException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean exists() {
        return load().isPresent();
    }

    private byte[] key() {
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalStateException("Thiếu docreel.youtube.encryption-key (đặt qua biến môi trường)");
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
