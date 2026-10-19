package com.carbonlink.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // Matched by PATTERN, not by an exact list, because an exact list silently fails in a way
    // that's very hard to diagnose live: "localhost", "127.0.0.1" and "[::1]" are three
    // different Origins to a browser even though they're the same machine. Opening the app at
    // http://127.0.0.1:5173 instead of http://localhost:5173 meant every single request was
    // blocked before it left the browser, and axios surfaces that as a bare "Network Error" —
    // indistinguishable, on screen, from the backend simply being down.
    //
    // Any loopback host on any port is allowed. Extra patterns come from
    // CORS_ALLOWED_ORIGIN_PATTERNS for a split frontend host. Same-origin deploys (the JAR
    // serving the built UI) do not need extra origins.
    private static final String[] LOOPBACK_ORIGIN_PATTERNS = {
            "http://localhost:[*]",
            "http://127.0.0.1:[*]",
            "http://[::1]:[*]"
    };

    @Value("${carbonlink.cors.allowed-origin-patterns:}")
    private String extraOriginPatterns;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> patterns = new ArrayList<>(Arrays.asList(LOOPBACK_ORIGIN_PATTERNS));
        if (extraOriginPatterns != null && !extraOriginPatterns.isBlank()) {
            for (String pattern : extraOriginPatterns.split(",")) {
                String trimmed = pattern.trim();
                if (!trimmed.isEmpty()) {
                    patterns.add(trimmed);
                }
            }
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(patterns.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

}
