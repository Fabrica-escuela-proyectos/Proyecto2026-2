package com.codefactory.reservas_backend.provider.controller.dto;

import java.util.UUID;

/** Antelación mínima de reserva vigente de un negocio (HU-08). */
public record BookingLeadTimeResponse(UUID businessId, int hours) {
}
