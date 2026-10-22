package com.smartcomplaint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class BCryptPasswordEncoderTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("Encode password and verify match")
    void testPasswordHashAndMatch() {
        String rawPassword = "SecurePassword@123";
        String encoded = encoder.encode(rawPassword);

        assertNotNull(encoded);
        assertTrue(encoded.startsWith("$2"));
        assertEquals(60, encoded.length());

        assertTrue(encoder.matches(rawPassword, encoded));
        assertFalse(encoder.matches("WrongPassword", encoded));
    }

    @Test
    @DisplayName("Verify known seed hash matches Admin@123")
    void testKnownAdminHash() {
        String adminHash = "$2a$10$92z3Sgjgk4rr5cMKKxpUSeyTj5/Q1dmVi7QsVepaqw.HvOsTPAhQC";
        assertTrue(encoder.matches("Admin@123", adminHash));
        assertFalse(encoder.matches("Admin@wrong", adminHash));
    }

    @Test
    @DisplayName("Verify user password hash matches Test@1234")
    void testKnownUserHash() {
        String userHash = "$2a$10$oKT3CgYFTh2BkoOpHzObyODy4dOPeCIckP5UkB.bGTOZlbk1sCfJK";
        assertTrue(encoder.matches("Test@1234", userHash));
        assertFalse(encoder.matches("WrongUserPass", userHash));
    }
}
