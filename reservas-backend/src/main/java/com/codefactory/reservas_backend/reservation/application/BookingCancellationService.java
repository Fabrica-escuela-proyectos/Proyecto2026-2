package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;

import java.util.UUID;

/**
 * HU-25 - Cancelar una reserva como cliente. Libera el horario del recurso: la restricción
 * anti-overbooking de la base solo cuenta las reservas CONFIRMADAS. (HU-26 y HU-28 añadirán
 * las cancelaciones del proveedor y por eliminación de cuenta.)
 */
public interface BookingCancellationService {

    /** Horas mínimas entre la cancelación y el inicio de la reserva (regla fija de la plataforma). */
    int MIN_NOTICE_HOURS = 1;

    /**
     * @param reason motivo opcional escrito por el cliente (hasta 500 caracteres)
     * @throws com.codefactory.reservas_backend.reservation.domain.BookingNotFoundException la reserva no existe (404)
     * @throws org.springframework.security.access.AccessDeniedException la reserva es de otro cliente (403)
     * @throws com.codefactory.reservas_backend.reservation.domain.BookingNotCancellableException ya cancelada,
     *         completada o con menos de {@link #MIN_NOTICE_HOURS} hora de antelación (409)
     */
    BookingItem cancelAsClient(UUID bookingId, String reason, UserIdentity client, String originIp);
}
