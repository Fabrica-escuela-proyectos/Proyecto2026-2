package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingPageResponse;

/** HU-23 - Consulta de reservas (HU-24 añade la vista del negocio). Solo lectura. */
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
}
