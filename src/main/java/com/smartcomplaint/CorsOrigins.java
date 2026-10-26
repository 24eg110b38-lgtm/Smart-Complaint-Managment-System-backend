package com.smartcomplaint;

import org.springframework.lang.NonNull;

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

    @NonNull
    static String[] allowedOriginPatternsArray(String frontendUrl) {
        List<String> patterns = allowedOriginPatterns(frontendUrl);
        String[] origins = new String[patterns.size()];

        for (int index = 0; index < patterns.size(); index++) {
            origins[index] = patterns.get(index);
        }

        return origins;
    }
}
