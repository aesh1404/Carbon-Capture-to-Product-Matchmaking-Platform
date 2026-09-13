package com.carbonlink.config;

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
    // Any loopback host on any port is allowed. That's appropriate here: this is a local-only
    // demo app with no auth and no cookies (allowCredentials is left off), so the origin isn't
    // carrying any authority worth restricting. A deployed version would pin real origins.
    private static final String[] LOOPBACK_ORIGIN_PATTERNS = {
            "http://localhost:[*]",
            "http://127.0.0.1:[*]",
            "http://[::1]:[*]"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(LOOPBACK_ORIGIN_PATTERNS)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
