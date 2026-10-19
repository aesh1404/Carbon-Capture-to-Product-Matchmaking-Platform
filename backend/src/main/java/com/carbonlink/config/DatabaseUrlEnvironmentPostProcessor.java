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
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        databaseUrl = databaseUrl.trim();
        Map<String, Object> props = new HashMap<>();

        if (databaseUrl.startsWith("jdbc:")) {
            props.put("spring.datasource.url", databaseUrl);
            environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
            System.out.println("[DatabaseUrlEnvironmentPostProcessor] Using raw JDBC URL from DATABASE_URL");
            return;
        }

        if (!databaseUrl.startsWith("postgres://") && !databaseUrl.startsWith("postgresql://")) {
            return;
        }

        try {
            // Robust parsing without java.net.URI limitations (handles underscores in host, special chars in password)
            int schemeEnd = databaseUrl.indexOf("://");
            String afterScheme = databaseUrl.substring(schemeEnd + 3);

            String userInfo = null;
            String hostAndRest = afterScheme;

            int atIndex = afterScheme.lastIndexOf('@');
            if (atIndex != -1) {
                userInfo = afterScheme.substring(0, atIndex);
                hostAndRest = afterScheme.substring(atIndex + 1);
            }

            String username = "postgres";
            String password = "";
            if (userInfo != null) {
                int colon = userInfo.indexOf(':');
                if (colon >= 0) {
                    username = decode(userInfo.substring(0, colon));
                    password = decode(userInfo.substring(colon + 1));
                } else {
                    username = decode(userInfo);
                }
            }

            // Extract host, port, path, query
            String hostPort;
            String pathQuery = "";
            int slashIndex = hostAndRest.indexOf('/');
            int questionIndex = hostAndRest.indexOf('?');

            int splitPoint = -1;
            if (slashIndex != -1) {
                splitPoint = slashIndex;
            } else if (questionIndex != -1) {
                splitPoint = questionIndex;
            }

            if (splitPoint != -1) {
                hostPort = hostAndRest.substring(0, splitPoint);
                pathQuery = hostAndRest.substring(splitPoint);
            } else {
                hostPort = hostAndRest;
            }

            String host = hostPort;
            int port = 5432;
            int portColon = hostPort.lastIndexOf(':');
            if (portColon != -1) {
                host = hostPort.substring(0, portColon);
                try {
                    port = Integer.parseInt(hostPort.substring(portColon + 1));
                } catch (NumberFormatException ignored) {}
            }

            String path = "/carbonlink";
            String query = "";
            if (!pathQuery.isEmpty()) {
                int qIdx = pathQuery.indexOf('?');
                if (qIdx != -1) {
                    path = pathQuery.substring(0, qIdx);
                    query = pathQuery.substring(qIdx + 1);
                } else {
                    path = pathQuery;
                }
            }
            if (path.isEmpty() || "/".equals(path)) {
                path = "/carbonlink";
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            String jdbc = "jdbc:postgresql://" + host + ":" + port + path;
            if (query.isEmpty()) {
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

            System.out.println("[DatabaseUrlEnvironmentPostProcessor] Configured DataSource -> "
                    + "host=" + host + ", port=" + port + ", database=" + path + ", user=" + username
                    + " (JDBC URL: " + jdbc + ")");
        } catch (Exception e) {
            System.err.println("[DatabaseUrlEnvironmentPostProcessor] Failed to parse DATABASE_URL: " + e.getMessage());
            e.printStackTrace();
        }
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
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
