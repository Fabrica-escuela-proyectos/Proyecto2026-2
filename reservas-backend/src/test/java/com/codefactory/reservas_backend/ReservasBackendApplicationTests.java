package com.codefactory.reservas_backend;

import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Comprueba que el contexto completo arranca contra un PostgreSQL real con
 * las migraciones de Flyway y el esquema validado por Hibernate
 * ({@code ddl-auto: validate}). Usa el contenedor de
 * {@link AbstractIntegrationTest}, así que no depende de un PostgreSQL local
 * y también corre en CI.
 */
class ReservasBackendApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
