package com.teample.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * DB에 저장하는 외부 서비스 토큰(카카오 등)을 AES-GCM으로 감싼다.
 * APP_TOKEN_ENCRYPTION_KEY가 없으면 암호화 없이 저장하되 "plain:" 접두어로 구분한다.
 * 단, 카카오 연동(KAKAO_REST_API_KEY)이 켜져 있는데 암호화 키가 없으면 기동을 거부한다.
 */
@Component
public class SecretCipher {

    private static final String ENCRYPTED_PREFIX = "enc:";
    private static final String PLAIN_PREFIX = "plain:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(String rawKey) {
        this(rawKey, "");
    }

    @Autowired
    public SecretCipher(
            @Value("${app.security.token-encryption-key:}") String rawKey,
            @Value("${kakao.rest-api-key:}") String kakaoRestApiKey
    ) {
        if (rawKey == null || rawKey.isBlank()) {
            if (kakaoRestApiKey != null && !kakaoRestApiKey.isBlank()) {
                throw new IllegalStateException(
                        "KAKAO_REST_API_KEY가 설정되어 있지만 APP_TOKEN_ENCRYPTION_KEY가 비어 있습니다. "
                                + "카카오 토큰을 평문으로 저장할 수 없으니 backend/.env에 APP_TOKEN_ENCRYPTION_KEY(긴 임의 문자열)를 넣고 다시 시작해 주세요.");
            }
            this.key = null;
        } else {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.trim().getBytes(StandardCharsets.UTF_8));
                this.key = new SecretKeySpec(digest, "AES");
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("토큰 암호화 키를 준비하지 못했습니다.", e);
            }
        }
    }

    public boolean isEncryptionEnabled() {
        return key != null;
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        if (key == null) {
            return PLAIN_PREFIX + plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("토큰을 암호화하지 못했습니다.", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (stored.startsWith(PLAIN_PREFIX)) {
            return stored.substring(PLAIN_PREFIX.length());
        }
        if (!stored.startsWith(ENCRYPTED_PREFIX)) {
            return stored;
        }
        if (key == null) {
            throw new IllegalStateException("암호화된 토큰을 풀 수 있는 키(APP_TOKEN_ENCRYPTION_KEY)가 없습니다.");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(ENCRYPTED_PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] decrypted = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("토큰을 복호화하지 못했습니다.", e);
        }
    }
}
