package com.codefactory.reservas_backend.reservation.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.identity.application.UserDeletionRequested;
import com.codefactory.reservas_backend.provider.application.BusinessDirectoryService;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDeletionBookingListenerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private BookingBulkCancellationService bulkCancellation;
    @Mock
    private BusinessDirectoryService businessDirectoryService;
    @Mock
    private AuditService auditService;

    private AccountDeletionBookingListener listener;

    @BeforeEach
    void setUp() {
        listener = new AccountDeletionBookingListener(bulkCancellation, businessDirectoryService, auditService);
    }

    @Test
    void unClienteConReservasFuturasLasCancelaConOrigenEliminacionDeCuentaYAudita() {
        when(bulkCancellation.cancelFutureOfClient(USER_ID, CancelOrigin.ELIMINACION_CUENTA,
                AccountDeletionBookingListener.CLIENT_REASON)).thenReturn(3);
        when(businessDirectoryService.businessIdsOfUser(USER_ID)).thenReturn(List.of());

        listener.onUserDeletion(new UserDeletionRequested(USER_ID, "cliente@example.com", "CLIENTE"));

        verify(auditService).registerEvent(eq(AuditEventType.CANCELACION_RESERVA), eq("cliente@example.com"),
                eq("SUCCESS"), contains("3 reserva"), anyString());
    }

    @Test
    void unProveedorCancelaLasReservasFuturasDeTodosSusNegocios() {
        UUID b1 = UUID.randomUUID();
        UUID b2 = UUID.randomUUID();
        when(bulkCancellation.cancelFutureOfClient(any(), any(), anyString())).thenReturn(0);
        when(businessDirectoryService.businessIdsOfUser(USER_ID)).thenReturn(List.of(b1, b2));
        when(bulkCancellation.cancelFutureOfBusinesses(List.of(b1, b2), CancelOrigin.ELIMINACION_CUENTA,
                AccountDeletionBookingListener.PROVIDER_REASON)).thenReturn(5);

        listener.onUserDeletion(new UserDeletionRequested(USER_ID, "proveedor@example.com", "PROVEEDOR"));

        verify(auditService).registerEvent(eq(AuditEventType.CANCELACION_RESERVA), eq("proveedor@example.com"),
                eq("SUCCESS"), contains("5 en sus negocios"), anyString());
    }

    @Test
    void sinReservasFuturasNoAudita() {
        when(bulkCancellation.cancelFutureOfClient(any(), any(), anyString())).thenReturn(0);
        when(businessDirectoryService.businessIdsOfUser(USER_ID)).thenReturn(List.of());

        listener.onUserDeletion(new UserDeletionRequested(USER_ID, "x@example.com", "CLIENTE"));

        verify(auditService, never()).registerEvent(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void siFallaLaCancelacionLaExcepcionSePropagaParaQueSeDeshagaLaEliminacion() {
        when(bulkCancellation.cancelFutureOfClient(any(), any(), anyString())).thenThrow(new IllegalStateException("falla"));

        assertThatThrownBy(() -> listener.onUserDeletion(new UserDeletionRequested(USER_ID, "x@example.com", "CLIENTE")))
                .isInstanceOf(IllegalStateException.class);
        verify(auditService, never()).registerEvent(any(), anyString(), anyString(), anyString(), anyString());
    }
}
