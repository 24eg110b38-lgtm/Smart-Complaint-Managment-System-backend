package com.smartcomplaint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("a-very-secure-jwt-test-secret-key-of-at-least-32-chars-long", 3600000L);
    }

    @Test
    @DisplayName("Generate token and extract valid claims")
    void testGenerateAndValidateToken() {
        String token = jwtService.generateToken(42, "user@example.com", "USER");
        assertNotNull(token);
        assertFalse(token.isBlank());

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("42", jwtService.extractUserId(token));
        assertEquals("user@example.com", jwtService.extractEmail(token));
        assertEquals("USER", jwtService.extractRole(token));
    }

    @Test
    @DisplayName("Admin role token generation and extraction")
    void testAdminToken() {
        String token = jwtService.generateToken(1, "admin@example.com", "ADMIN");
        assertTrue(jwtService.isTokenValid(token));
        assertEquals("1", jwtService.extractUserId(token));
        assertEquals("ADMIN", jwtService.extractRole(token));
    }

    @Test
    @DisplayName("Reject invalid and tampered tokens")
    void testInvalidToken() {
        assertFalse(jwtService.isTokenValid("invalid.token.structure"));
        assertFalse(jwtService.isTokenValid(""));
        assertFalse(jwtService.isTokenValid(null));
    }

    @Test
    @DisplayName("Expired token is rejected")
    void testExpiredToken() {
        JwtService shortLivedService = new JwtService(
                "a-very-secure-jwt-test-secret-key-of-at-least-32-chars-long",
                -1000L // already expired
        );
        String expiredToken = shortLivedService.generateToken(99, "expired@test.com", "USER");
        assertFalse(jwtService.isTokenValid(expiredToken));
    }

    @Test
    @DisplayName("Reject missing or short JWT secrets")
    void testRejectMissingOrShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("", 0));
        assertThrows(IllegalArgumentException.class, () -> new JwtService("short", 0));
    }
}
