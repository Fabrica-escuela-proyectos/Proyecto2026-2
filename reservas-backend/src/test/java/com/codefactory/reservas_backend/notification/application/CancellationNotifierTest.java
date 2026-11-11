package com.codefactory.reservas_backend.notification.application;

import com.codefactory.reservas_backend.reservation.application.BookingsCancelled;
import com.codefactory.reservas_backend.reservation.application.BookingsCancelled.CancelledBooking;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CancellationNotifierTest {

    /** Lunes 16-nov-2026 15:00 en Bogotá. */
    private static final Instant START = Instant.parse("2026-11-16T20:00:00Z");

    @Mock
    private EmailSender emailSender;
    @InjectMocks
    private CancellationNotifier notifier;

    private static CancelledBooking booking(String email, String name) {
        return new CancelledBooking(UUID.randomUUID(), email, name, "Barbería Central", "Corte", "Sala 1", START);
    }

    @Test
    void elCorreoDiceQueServicioCuandoPorQueYElMotivoEnHoraDeColombia() {
        EmailMessage m = CancellationNotifier.compose(booking("ana@correo.co", "Ana"), "PROVEEDOR", "Mantenimiento");

        assertThat(m.toEmail()).isEqualTo("ana@correo.co");
        assertThat(m.subject()).isEqualTo("Tu reserva en Barbería Central fue cancelada");
        assertThat(m.text()).contains("Hola Ana:", "Corte en Barbería Central", "16/11/2026", "15:00 (hora de Colombia)",
                "el negocio la canceló", "Motivo: Mantenimiento");
    }

    @Test
    void cadaOrigenSeExplicaConSuPropiaCausa() {
        assertThat(CancellationNotifier.compose(booking("a@b.co", "A"), "SERVICIO_NO_DISPONIBLE", null).text())
                .contains("el servicio dejó de estar disponible").doesNotContain("Motivo:");
        assertThat(CancellationNotifier.compose(booking("a@b.co", "A"), "RECURSO_NO_DISPONIBLE", " ").text())
                .contains("no estará disponible en ese horario");
        assertThat(CancellationNotifier.compose(booking("a@b.co", null), "ELIMINACION_CUENTA", "x").text())
                .startsWith("Hola:").contains("ya no está en la plataforma");
    }

    @Test
    void avisaACadaClienteYSaltaLosQueYaNoTienenCorreo() {
        notifier.onBookingsCancelled(new BookingsCancelled("PROVEEDOR", "x",
                List.of(booking("uno@correo.co", "Uno"), booking(null, "Cliente eliminado"), booking("dos@correo.co", "Dos"))));

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(2)).send(sent.capture());
        assertThat(sent.getAllValues()).extracting(EmailMessage::toEmail).containsExactly("uno@correo.co", "dos@correo.co");
    }

    @Test
    void unFalloDelProveedorNoDetieneLosDemasAvisos() {
        doThrow(new EmailDeliveryException("Brevo respondió 500")).doNothing().when(emailSender).send(any());

        notifier.onBookingsCancelled(new BookingsCancelled("SERVICIO_NO_DISPONIBLE", "x",
                List.of(booking("uno@correo.co", "Uno"), booking("dos@correo.co", "Dos"))));

        verify(emailSender, times(2)).send(any());
    }
}
