package com.carbonlink.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Regression guard for a failure that is genuinely hard to diagnose live: a browser treats
// "localhost", "127.0.0.1" and "[::1]" as three different Origins. When only "localhost" was
// allow-listed, opening the app at http://127.0.0.1:5173 got every request blocked before it
// left the browser, and axios reported it as a bare "Network Error" - identical on screen to
// the backend being down, which sends you debugging the wrong thing entirely.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // Never touch the real file-based database: it runs with AUTO_SERVER=TRUE, so a test
        // would otherwise attach to whatever dev instance happens to be running.
        "spring.datasource.url=jdbc:h2:mem:corstest;DB_CLOSE_DELAY=-1"
})
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightIsAllowedFromLocalhost() throws Exception {
        assertPreflightAllowed("http://localhost:5173");
    }

    @Test
    void preflightIsAllowedFromTheIpv4Loopback() throws Exception {
        // The spelling that was broken.
        assertPreflightAllowed("http://127.0.0.1:5173");
    }

    @Test
    void preflightIsAllowedFromTheIpv6Loopback() throws Exception {
        assertPreflightAllowed("http://[::1]:5173");
    }

    @Test
    void preflightIsAllowedOnAnyLoopbackPort() throws Exception {
        // Vite's preview server, a second dev server, or whatever port is free that day.
        assertPreflightAllowed("http://localhost:3000");
        assertPreflightAllowed("http://127.0.0.1:4173");
    }

    @Test
    void preflightIsRefusedFromANonLoopbackOrigin() throws Exception {
        mockMvc.perform(options("/api/listings")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private void assertPreflightAllowed(String origin) throws Exception {
        mockMvc.perform(options("/api/listings")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }
}
