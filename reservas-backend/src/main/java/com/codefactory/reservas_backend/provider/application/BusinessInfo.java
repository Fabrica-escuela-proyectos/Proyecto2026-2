package com.codefactory.reservas_backend.provider.application;

import java.util.UUID;

/** Datos públicos mínimos de un negocio que Provider expone a otros módulos (ADR-003). */
public record BusinessInfo(UUID id, String name) {
}
