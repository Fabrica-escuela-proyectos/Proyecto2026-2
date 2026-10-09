package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.provider.controller.dto.BookingLeadTimeResponse;

import java.util.UUID;

/**
 * Configuración de un negocio que cambia su propio proveedor (HU-08:
 * antelación mínima de reserva).
 */
public interface BusinessSettingsService {

    /** Antelación vigente; solo el dueño del negocio. */
    BookingLeadTimeResponse getBookingLeadTime(UUID businessId, UserIdentity requester);

    /** Cambia la antelación (1..720 horas); solo el dueño. No afecta reservas ya confirmadas. */
    BookingLeadTimeResponse updateBookingLeadTime(UUID businessId, int hours, UserIdentity requester, String originIp);

    /**
     * Para otros módulos (HU-22 al crear una reserva): antelación mínima del
     * negocio, sin comprobar al usuario porque quien reserva es un cliente.
     *
     * @throws com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException si no existe
     */
    int minAdvanceHoursOf(UUID businessId);
}
