package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingPageResponse;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BusinessBookingPageResponse;

import java.util.UUID;

/** HU-23 (reservas del cliente) y HU-24 (reservas del negocio). Solo lectura. */
public interface BookingQueryService {

    int DEFAULT_SIZE = 20;
    int MAX_SIZE = 50;

    /**
     * Reservas del cliente autenticado, de la más reciente a la más antigua (por fecha de inicio).
     *
     * @param status filtro opcional (CONFIRMADA, CANCELADA o COMPLETADA; sin distinguir mayúsculas)
     * @param page   página desde 0
     * @param size   elementos por página; se recorta a {@link #MAX_SIZE}
     * @throws com.codefactory.reservas_backend.common.error.InvalidPaginationException si page &lt; 0 o size &lt; 1 (400)
     * @throws com.codefactory.reservas_backend.reservation.domain.InvalidBookingException si el estado no existe (400)
     */
    BookingPageResponse listOwn(UserIdentity client, String status, int page, int size);

    /**
     * HU-24 - Reservas de un negocio, para su PROVEEDOR dueño, de la más reciente a la más antigua.
     *
     * @param from   fecha inicial {@code yyyy-MM-dd} (inclusive, sobre la fecha de inicio de la reserva en Bogotá); opcional
     * @param to     fecha final {@code yyyy-MM-dd} (inclusive); opcional
     * @param status filtro opcional de estado
     * @throws com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException si el negocio no existe (404)
     * @throws org.springframework.security.access.AccessDeniedException si el negocio es de otro proveedor (403)
     * @throws com.codefactory.reservas_backend.reservation.domain.InvalidBookingException fecha o estado inválidos, o {@code from} posterior a {@code to} (400)
     */
    BusinessBookingPageResponse listForBusiness(UUID businessId, UserIdentity provider, String from, String to,
                                                String status, int page, int size);
}
