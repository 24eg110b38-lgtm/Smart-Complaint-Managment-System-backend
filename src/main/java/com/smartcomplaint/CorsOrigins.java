package com.smartcomplaint;

import java.util.ArrayList;
import java.util.List;

final class CorsOrigins {

    private CorsOrigins() {
    }

    static List<String> allowedOriginPatterns(String frontendUrl) {
        List<String> origins = new ArrayList<>(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"));

        if (frontendUrl != null && !frontendUrl.isBlank()) {
            origins.add(frontendUrl.trim());
        }

        return List.copyOf(origins);
    }
}
