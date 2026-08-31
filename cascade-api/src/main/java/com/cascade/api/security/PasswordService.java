package com.cascade.api.security;

import java.security.SecureRandom;
import java.util.Base64;
import org.apache.shiro.crypto.hash.DefaultHashService;
import org.apache.shiro.crypto.hash.Hash;
import org.apache.shiro.crypto.hash.HashRequest;
import org.apache.shiro.crypto.hash.HashService;
import org.apache.shiro.util.ByteSource;

/**
 * Password hashing built on Shiro's hash service.
 *
 * <p>Stored form is {@code iterations$salt$hash}, all base64, so the work
 * factor can be raised later without invalidating existing credentials.
 */
public final class PasswordService {

    private static final int ITERATIONS = 200_000;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final HashService hashService;

    public PasswordService() {
        DefaultHashService service = new DefaultHashService();
        service.setHashAlgorithmName("SHA-256");
        service.setHashIterations(ITERATIONS);
        service.setGeneratePublicSalt(false);
        this.hashService = service;
    }

    public String hash(String plaintext) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        Hash hash = hashService.computeHash(new HashRequest.Builder()
                .setSource(ByteSource.Util.bytes(plaintext))
                .setSalt(ByteSource.Util.bytes(salt))
                .setIterations(ITERATIONS)
                .build());
        return ITERATIONS
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + hash.toBase64();
    }

    public boolean verify(String plaintext, String stored) {
        if (stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 3) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            Hash candidate = hashService.computeHash(new HashRequest.Builder()
                    .setSource(ByteSource.Util.bytes(plaintext))
                    .setSalt(ByteSource.Util.bytes(salt))
                    .setIterations(iterations)
                    .build());
            return constantTimeEquals(candidate.toBase64(), parts[2]);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Avoids leaking how much of the hash matched through timing. */
    private static boolean constantTimeEquals(String a, String b) {
        byte[] left = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] right = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(left, right);
    }
}
