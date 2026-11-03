package com.codefactory.reservas_backend.resource.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;

import java.util.List;
import java.util.UUID;

/**
 * Cierres puntuales de un recurso (HU-21). Crear un cierre puede cancelar reservas, así que esa parte la
 * orquesta el módulo de reservas ({@code ResourceClosureBookingService}); aquí viven la validación del
 * periodo, el registro, la consulta y la eliminación.
 */
public interface ResourceClosureService {

    /**
     * Valida y convierte el periodo pedido: un día completo ({@code date}) o un rango ({@code start} y
     * {@code end} con fecha y hora, {@code yyyy-MM-dd HH:mm} o {@code yyyy-MM-ddTHH:mm}). Exige fin posterior
     * al inicio, que la fecha no sea anterior a hoy y que el cierre aún no haya terminado.
     *
     * @throws com.codefactory.reservas_backend.resource.domain.InvalidClosureException periodo inválido (400)
     */
    ClosurePeriod resolvePeriod(String date, String start, String end);

    /** 409 si el periodo se cruza con otro cierre del recurso. El llamador ya tiene el recurso bloqueado. */
    void requireNoOverlap(UUID resourceId, ClosurePeriod period);

    /** Guarda el cierre y lo audita con la cantidad de reservas canceladas. El llamador ya validó todo. */
    ClosureInfo create(UUID resourceId, ClosurePeriod period, String reason, int cancelledBookings,
                       UserIdentity requester, String originIp);

    /** Cierres que aún no terminan, del más próximo al más lejano. Solo el proveedor dueño. */
    List<ClosureInfo> listUpcoming(UUID resourceId, UserIdentity requester);

    /** Elimina un cierre que todavía no empezó (409 si ya empezó). Solo el proveedor dueño. */
    void delete(UUID resourceId, UUID closureId, UserIdentity requester, String originIp);
}
