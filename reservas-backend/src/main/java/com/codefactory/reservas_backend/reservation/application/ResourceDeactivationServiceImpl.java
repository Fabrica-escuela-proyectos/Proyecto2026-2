package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.domain.ConfirmationRequiredException;
import com.codefactory.reservas_backend.resource.application.ResourceInfo;
import com.codefactory.reservas_backend.resource.application.ResourceLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceDeactivationServiceImpl implements ResourceDeactivationService {

    private final ResourceLifecycleService lifecycleService;
    private final BookingBulkCancellationService bulkCancellation;

    @Override
    @Transactional
    public DeactivationResult deactivate(UUID resourceId, boolean confirm, UserIdentity requester, String originIp) {
        // Con el recurso bloqueado: ninguna reserva nueva puede colarse entre el conteo y la desactivación.
        ResourceInfo resource = lifecycleService.lockOwnedResource(resourceId, requester);
        if (!resource.active()) {
            return new DeactivationResult(resource.id(), resource.name(), false, 0);
        }

        long affected = bulkCancellation.countFutureOfResource(resourceId);
        if (affected > 0 && !confirm) {
            throw new ConfirmationRequiredException("El recurso tiene " + affected + " reserva(s) futura(s) confirmada(s) que "
                    + "se cancelarán si continúa. Confirme la desactivación para proceder", affected);
        }

        // Todo en una sola transacción: si algo falla, ni el recurso queda inactivo ni se cancela ninguna reserva.
        int cancelled = affected == 0 ? 0
                : bulkCancellation.cancelFutureOfResource(resourceId, CancelOrigin.RECURSO_NO_DISPONIBLE, CANCELLATION_REASON);
        lifecycleService.deactivate(resourceId, requester, cancelled, originIp);
        return new DeactivationResult(resource.id(), resource.name(), false, cancelled);
    }
}
