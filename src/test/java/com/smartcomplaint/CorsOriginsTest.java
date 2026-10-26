package com.smartcomplaint;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorsOriginsTest {

    @Test
    void includesProductionFrontendAndLocalDevelopmentOrigins() {
        assertEquals(
                List.of(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "https://*.vercel.app",
                        "https://complaints.example.com"),
                CorsOrigins.allowedOriginPatterns(" https://complaints.example.com "));
    }

    @Test
    void keepsLocalOriginsWhenProductionFrontendIsUnset() {
        assertEquals(
                List.of("http://localhost:*", "http://127.0.0.1:*", "https://*.vercel.app"),
                CorsOrigins.allowedOriginPatterns(""));
    }
}
