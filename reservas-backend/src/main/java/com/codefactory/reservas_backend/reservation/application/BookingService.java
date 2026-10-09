package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingResponse;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.CreateBookingRequest;

/** HU-22 - Crear reserva (HU-23..28 consultan y cambian su estado). */
public interface BookingService {

    /**
     * Crea una reserva CONFIRMADA para el cliente autenticado.
     *
     * @throws com.codefactory.reservas_backend.reservation.domain.InvalidBookingException fecha/rango/antelación/duración/recurso inválidos (400)
     * @throws com.codefactory.reservas_backend.service.domain.ServiceNotAvailableException servicio inexistente, inactivo o de un proveedor inactivo (404)
     * @throws com.codefactory.reservas_backend.reservation.domain.SlotNotAvailableException horario ocupado o fuera del horario del recurso (409)
     */
    BookingResponse create(CreateBookingRequest request, UserIdentity client, String originIp);
}
