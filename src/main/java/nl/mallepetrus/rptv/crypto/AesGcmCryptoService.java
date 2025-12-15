package nl.mallepetrus.rptv.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AesGcmCryptoService implements CryptoService {

    private static final String ALGO = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12; // recommended 96-bit IV

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCryptoService(@Value("${app.enc.master-key}") String b64Key) {
        if (b64Key == null || b64Key.isBlank()) {
            throw new IllegalStateException("Missing required property app.enc.master-key (Base64-encoded 256-bit key)");
        }
        byte[] keyBytes = Base64.getDecoder().decode(b64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("app.enc.master-key must be 32 bytes (256-bit) when Base64-decoded");
        }
        this.key = new SecretKeySpec(keyBytes, ALGO);
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // package as: [version=1 byte][iv][cipher]
            byte version = 1;
            ByteBuffer bb = ByteBuffer.allocate(1 + iv.length + cipherBytes.length);
            bb.put(version);
            bb.put(iv);
            bb.put(cipherBytes);
            return Base64.getEncoder().encodeToString(bb.array());
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null) return null;
        try {
            byte[] packed = Base64.getDecoder().decode(cipherText);
            ByteBuffer bb = ByteBuffer.wrap(packed);
            byte version = bb.get();
            if (version != 1) {
                throw new IllegalStateException("Unsupported crypto payload version: " + version);
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            bb.get(iv);
            byte[] cipherBytes = new byte[bb.remaining()];
            bb.get(cipherBytes);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    // helper to generate a random key for local use (not used by runtime)
    public static String generateRandomKeyB64() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(ALGO);
            kg.init(256);
            SecretKey key = kg.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
