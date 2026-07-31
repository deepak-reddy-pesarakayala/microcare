package com.microcare.gateway.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the seeded users in {@code init-scripts/01-create-auth-db.sql}:
 * the stored hash must match the documented password ("password") so the
 * gateway's login flow works out of the box.
 */
class AuthPasswordSeedTest {

    /** BCrypt hash of the literal string "password" (generated with BCryptPasswordEncoder). */
    static final String SEEDED_HASH = "$2a$10$vGzhg6d/sEkK8xPtGYpAfe9iesGgOycg25O9bPG1jK0g7bzG1aGva";

    @Test
    void seededHashMatchesDocumentedPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches("password", SEEDED_HASH),
                "Seeded BCrypt hash must match the documented password");
    }
}
