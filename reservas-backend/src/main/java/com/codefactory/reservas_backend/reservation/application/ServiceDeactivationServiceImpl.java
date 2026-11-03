package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.domain.ConfirmationRequiredException;
import com.codefactory.reservas_backend.service.application.ServiceInfo;
import com.codefactory.reservas_backend.service.application.ServiceLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceDeactivationServiceImpl implements ServiceDeactivationService {

    private final ServiceLifecycleService lifecycleService;
    private final BookingBulkCancellationService bulkCancellation;

    @Override
    @Transactional
    public DeactivationResult deactivate(UUID serviceId, boolean confirm, UserIdentity requester, String originIp) {
        // Con el servicio bloqueado (FOR UPDATE) ninguna reserva nueva entra entre el conteo y la desactivación:
        // cada reserva toma un bloqueo compartido del servicio (ServiceLookupService.lockForBooking).
        ServiceInfo service = lifecycleService.lockOwnedService(serviceId, requester);
        if (!service.active()) {
            return new DeactivationResult(service.id(), service.name(), false, 0);
        }

        long affected = bulkCancellation.countFutureOfService(serviceId);
        if (affected > 0 && !confirm) {
            throw new ConfirmationRequiredException("El servicio tiene " + affected + " reserva(s) futura(s) confirmada(s) que "
                    + "se cancelarán si continúa. Confirme la desactivación para proceder", affected);
        }

        int cancelled = affected == 0 ? 0
                : bulkCancellation.cancelFutureOfService(serviceId, CancelOrigin.SERVICIO_NO_DISPONIBLE, CANCELLATION_REASON);
        lifecycleService.deactivate(serviceId, requester, cancelled, originIp);
        return new DeactivationResult(service.id(), service.name(), false, cancelled);
    }
}
