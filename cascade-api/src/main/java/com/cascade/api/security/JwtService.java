package com.cascade.api.security;

import com.cascade.core.model.Role;
import com.cascade.core.model.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** Issues and verifies the HS256 session tokens used by the API and CLI. */
public class JwtService {

    private static final String ISSUER = "cascade";
    private static final String AUDIENCE = "cascade-client";

    private final byte[] secret;
    private final int ttlHours;

    public JwtService(String secret, int ttlHours) {
        this.secret = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.ttlHours = ttlHours;
        if (this.secret.length < 32) {
            // HS256 needs at least 256 bits of key material.
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
    }

    public record Claims(String userId, String email, Role role) { }

    public String issue(User user) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.getId())
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttlHours, ChronoUnit.HOURS)))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException("could not sign the session token", e);
        }
        return jwt.serialize();
    }

    /** Returns empty for any token that is malformed, unsigned, or expired. */
    public Optional<Claims> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer())
                    || claims.getAudience() == null
                    || !claims.getAudience().contains(AUDIENCE)) {
                return Optional.empty();
            }
            Date expiry = claims.getExpirationTime();
            if (expiry == null || expiry.toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            return Optional.of(new Claims(
                    claims.getSubject(),
                    String.valueOf(claims.getClaim("email")),
                    Role.parse(String.valueOf(claims.getClaim("role")))));
        } catch (ParseException | JOSEException e) {
            return Optional.empty();
        }
    }
}
