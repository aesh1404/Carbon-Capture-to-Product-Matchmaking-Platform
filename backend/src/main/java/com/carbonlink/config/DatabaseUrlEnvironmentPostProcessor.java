package com.carbonlink.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Hosts such as Render, Neon, and Supabase inject {@code DATABASE_URL} as
 * {@code postgres://user:pass@host:port/db}. Spring's DataSource expects a JDBC URL plus
 * separate username/password, so this maps one into the other before context refresh.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = firstNonBlank(
                environment.getProperty("DATABASE_URL"),
                System.getenv("DATABASE_URL"));
        if (databaseUrl == null) {
            return;
        }

        Map<String, Object> props = new HashMap<>();
        if (databaseUrl.startsWith("jdbc:")) {
            props.put("spring.datasource.url", databaseUrl);
            environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
            return;
        }

        if (!databaseUrl.startsWith("postgres://") && !databaseUrl.startsWith("postgresql://")) {
            return;
        }

        URI uri = URI.create(databaseUrl);
        String userInfo = uri.getUserInfo();
        if (userInfo == null || uri.getHost() == null) {
            return;
        }

        int colon = userInfo.indexOf(':');
        String username = colon >= 0 ? decode(userInfo.substring(0, colon)) : decode(userInfo);
        String password = colon >= 0 ? decode(userInfo.substring(colon + 1)) : "";
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            path = "/carbonlink";
        }

        String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + path;
        String query = uri.getQuery();
        if (query == null || query.isBlank()) {
            // prefer: TLS when the host offers it (Render/Neon), plain TCP for local Docker.
            jdbc += "?sslmode=prefer";
        } else {
            jdbc += "?" + query;
            if (!query.contains("sslmode=")) {
                jdbc += "&sslmode=prefer";
            }
        }

        props.put("spring.datasource.url", jdbc);
        props.put("spring.datasource.username", username);
        props.put("spring.datasource.password", password);
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
