package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.domain.ConfirmationRequiredException;
import com.codefactory.reservas_backend.resource.application.ClosureInfo;
import com.codefactory.reservas_backend.resource.application.ClosurePeriod;
import com.codefactory.reservas_backend.resource.application.ResourceClosureService;
import com.codefactory.reservas_backend.resource.application.ResourceLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceClosureBookingServiceImpl implements ResourceClosureBookingService {

    private final ResourceLifecycleService lifecycleService;
    private final ResourceClosureService closureService;
    private final BookingBulkCancellationService bulkCancellation;

    @Override
    @Transactional
    public ClosureResult close(UUID resourceId, String date, String start, String end, String reason, boolean confirm,
                               UserIdentity requester, String originIp) {
        ClosurePeriod period = closureService.resolvePeriod(date, start, end);
        // Con el recurso bloqueado: ninguna reserva entra entre el conteo y el cierre (BookingServiceImpl
        // bloquea el mismo recurso y vuelve a comprobar los cierres con el bloqueo tomado).
        lifecycleService.lockOwnedResource(resourceId, requester);
        closureService.requireNoOverlap(resourceId, period);

        long affected = bulkCancellation.countFutureOfResourceIn(resourceId, period.start(), period.end());
        if (affected > 0 && !confirm) {
            throw new ConfirmationRequiredException("En ese periodo hay " + affected + " reserva(s) confirmada(s) que se "
                    + "cancelarán si continúa. Confirme el cierre para proceder", affected);
        }
        int cancelled = affected == 0 ? 0 : bulkCancellation.cancelFutureOfResourceIn(
                resourceId, period.start(), period.end(), CancelOrigin.RECURSO_NO_DISPONIBLE, CANCELLATION_REASON);

        // El aviso a los clientes afectados es el evento de auditoría (no hay correo; plan del Sprint 3, D12).
        ClosureInfo closure = closureService.create(resourceId, period, reason, cancelled, requester, originIp);
        String message = cancelled == 0
                ? "El recurso quedó cerrado en ese periodo"
                : "El recurso quedó cerrado en ese periodo y se cancelaron " + cancelled + " reserva(s)";
        return new ClosureResult(closure, cancelled, message);
    }
}
