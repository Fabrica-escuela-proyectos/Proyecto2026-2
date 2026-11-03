package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.controller.dto.BookingDtos.BookingItem;

import java.util.UUID;

/**
 * HU-27 - Completar reserva. Solo el PROVEEDOR dueño del negocio de la reserva, solo una reserva
 * CONFIRMADA y solo cuando su hora de fin ya pasó (plan del Sprint 3, decisión D4).
 */
public interface BookingCompletionService {

    BookingItem complete(UUID bookingId, UserIdentity provider, String originIp);
}
