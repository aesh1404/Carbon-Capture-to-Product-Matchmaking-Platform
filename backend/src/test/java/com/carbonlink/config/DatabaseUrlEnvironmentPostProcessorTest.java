package com.carbonlink.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    void mapsPostgresUriToJdbcProperties() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("DATABASE_URL", "postgres://carbonlink:s3cret@db.example:5432/carbonlink");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.example:5432/carbonlink?sslmode=prefer");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("carbonlink");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("s3cret");
        assertThat(environment.getProperty("spring.datasource.driver-class-name"))
                .isEqualTo("org.postgresql.Driver");
    }

    @Test
    void leavesJdbcUrlsAlone() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("DATABASE_URL", "jdbc:postgresql://localhost:5432/carbonlink");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/carbonlink");
        assertThat(environment.getProperty("spring.datasource.username")).isNull();
    }
}
