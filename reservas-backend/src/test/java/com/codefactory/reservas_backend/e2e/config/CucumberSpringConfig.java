package com.codefactory.reservas_backend.e2e.config;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Levanta la aplicación completa con un servidor HTTP real (puerto aleatorio) y un PostgreSQL 16
 * en contenedor. Es la única clase con @CucumberContextConfiguration: Cucumber exige que haya una sola.
 * Requiere Docker, igual que las pruebas de integración.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CucumberSpringConfig {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("reservas__e2e")
            .withUsername("reservas__e2e")
            .withPassword("reservas__e2e");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configurarBaseDeDatos(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
