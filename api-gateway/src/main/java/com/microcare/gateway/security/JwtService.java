package com.microcare.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Signs and validates JWTs for the API Gateway.
 *
 * <p>Claims embedded in every token:
 * <ul>
 *   <li>{@code sub}  — username</li>
 *   <li>{@code uid}  — user id (Users table PK)</li>
 *   <li>{@code role} — ADMIN | DOCTOR | PATIENT</li>
 *   <li>{@code pid}  — patient id (only for PATIENT-role users)</li>
 *   <li>{@code did}  — doctor id (only for DOCTOR-role users)</li>
 *   <li>{@code iat}/{@code exp} — issued-at / expiration timestamps</li>
 * </ul>
 */
@Service
public class JwtService {

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_PID = "pid";
    private static final String CLAIM_DID = "did";

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms:86400000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Creates a signed JWT for the given user.
     *
     * @param username  unique login name
     * @param userId    Users table primary key
     * @param role      user role (ADMIN/DOCTOR/PATIENT)
     * @param patientId patient id (nullable — only meaningful for PATIENT role)
     * @param doctorId  doctor id (nullable — only meaningful for DOCTOR role)
     */
    public String createToken(String username, Long userId, String role, Long patientId, Long doctorId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
                .subject(username)
                .claim(CLAIM_UID, userId)
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key);

        if (patientId != null) {
            builder.claim(CLAIM_PID, patientId);
        }
        if (doctorId != null) {
            builder.claim(CLAIM_DID, doctorId);
        }

        return builder.compact();
    }

    /**
     * Validates the token's signature and expiry and returns its claims.
     *
     * @throws JwtException  if the token is malformed, tampered, or expired
     */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * @return configured token lifetime in milliseconds
     */
    public long expirationMs() {
        return expirationMs;
    }
}
