package com.codefactory.reservas_backend;

import com.codefactory.reservas_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el contexto completo arranca contra un PostgreSQL real con
 * las migraciones de Flyway y el esquema validado por Hibernate
 * ({@code ddl-auto: validate}). Usa el contenedor de
 * {@link AbstractIntegrationTest}, así que no depende de un PostgreSQL local
 * y también corre en CI.
 */
class ReservasBackendApplicationTests extends AbstractIntegrationTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.getBeanDefinitionCount()).isPositive();
	}

}
