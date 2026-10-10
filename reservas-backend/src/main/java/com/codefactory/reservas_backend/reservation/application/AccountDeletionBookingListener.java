package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserDeletionRequested;
import com.codefactory.reservas_backend.provider.application.BusinessDirectoryService;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * HU-28 - Cuando se elimina una cuenta, cancela sus reservas FUTURAS con origen {@code ELIMINACION_CUENTA}:
 * las que el usuario hizo como cliente y las de los negocios que tenía como proveedor. Corre de forma síncrona
 * en la misma transacción de la eliminación (evento {@link UserDeletionRequested}), así que o se cancela y se
 * elimina todo, o no se hace nada. No aplica la regla de antelación de 1 hora. Las reservas pasadas, las ya
 * canceladas y las completadas se conservan como historial: {@code bookings} guarda copias del nombre, correo,
 * servicio, recurso y precio, y sus referencias pasan a nulas al borrarse (ON DELETE SET NULL).
 * Los servicios y recursos del proveedor se eliminan por cascada, así que dejan de ser reservables.
 */
@Component
@RequiredArgsConstructor
public class AccountDeletionBookingListener {

    static final String CLIENT_REASON = "La cuenta del cliente fue eliminada";
    static final String PROVIDER_REASON = "La cuenta del proveedor fue eliminada";

    private final BookingBulkCancellationService bulkCancellation;
    private final BusinessDirectoryService businessDirectoryService;
    private final AuditService auditService;

    @EventListener
    public void onUserDeletion(UserDeletionRequested event) {
        int asClient = bulkCancellation.cancelFutureOfClient(event.userId(), CancelOrigin.ELIMINACION_CUENTA, CLIENT_REASON);

        List<UUID> businessIds = businessDirectoryService.businessIdsOfUser(event.userId());
        int asProvider = bulkCancellation.cancelFutureOfBusinesses(businessIds, CancelOrigin.ELIMINACION_CUENTA, PROVIDER_REASON);

        if (asClient + asProvider > 0) {
            // El aviso a las partes afectadas (fuera de alcance) queda como evento de auditoría.
            auditService.registerEvent(AuditEventType.CANCELACION_RESERVA, event.email(), "SUCCESS",
                    "Eliminación de cuenta: " + asClient + " reserva(s) futura(s) como cliente y " + asProvider
                            + " en sus negocios canceladas (origen ELIMINACION_CUENTA)", "sistema");
        }
    }
}
