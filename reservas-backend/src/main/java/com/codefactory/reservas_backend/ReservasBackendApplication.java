package com.codefactory.reservas_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del backend. Estructura modular por dominios de negocio
 * (identity, audit, common - providers se agregara en HU03), segun el
 * monolito modular acordado en la Agenda de Planning y en
 * Sprint_1_ArquisuaveBD.docx.
 */


@SpringBootApplication
public class ReservasBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReservasBackendApplication.class, args);
	}

}
