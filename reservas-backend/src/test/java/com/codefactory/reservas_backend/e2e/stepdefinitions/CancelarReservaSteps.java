package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.models.EstadosDeReserva;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReservas;
import com.codefactory.reservas_backend.e2e.tasks.CancelarReserva;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarReservas;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarReservas;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.prepararAgendaYCliente;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.primeraReserva;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.reservarPorLaApi;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.MOTIVO;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.OTRO_CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * HU-25 (cancelar como cliente) y HU-26 (cancelar como proveedor). Los pasos que comparten con otras HU
 * ("the booking is in status", "the cancellation is rejected"...) están en ReservasComunesSteps.
 */
public class CancelarReservaSteps {

    private static final String MOTIVO_DEL_PROVEEDOR = "El recurso está en mantenimiento";

    @Autowired
    private BookingRepository bookingRepository;

    // ---------- Given (HU-25) ----------

    @Given("a client has a confirmed booking that starts in more than 1 hour")
    @Given("a provider has a confirmed booking in their business")
    public void reservaConfirmadaFutura() {
        Agenda agenda = prepararAgendaYCliente();
        guardarReservas(List.of(reservarPorLaApi(theActorCalled(CLIENTE), agenda, 7)));
    }

    @Given("a client cancelled a booking with enough notice")
    public void clienteCanceloConAntelacion() {
        reservaConfirmadaFutura();
        Actor cliente = theActorCalled(CLIENTE);
        cliente.attemptsTo(CancelarReserva.comoCliente(primeraReserva()));
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    /** Una reserva futura que ya está cancelada o completada: se guarda directo en la base. */
    @Given("a client has a booking in status {string}")
    @Given("a provider has a booking in status {string}")
    public void reservaEnEstado(String estado) {
        Agenda agenda = prepararAgendaYCliente();
        Instant enDosDias = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        guardarReservas(List.of(AgendaDePrueba.insertarReserva(bookingRepository, agenda, theActorCalled(CLIENTE),
                enDosDias, EstadosDeReserva.estado(estado))));
    }

    @Given("a client has logged in")
    public void unClienteHaIniciadoSesion() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(CLIENTE), "Cliente");
    }

    // ---------- Given (HU-26) ----------

    @Given("a provider cancelled a booking giving a reason")
    public void proveedorCanceloConMotivo() {
        reservaConfirmadaFutura();
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.attemptsTo(CancelarReserva.comoProveedor(primeraReserva(), MOTIVO_DEL_PROVEEDOR));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        contexto().remember(MOTIVO, MOTIVO_DEL_PROVEEDOR);
    }

    // ---------- When (HU-25) ----------

    @When("the client cancels the booking")
    @When("the client tries to cancel it")
    public void elClienteCancela() {
        String reserva = primeraReserva();
        theActorCalled(CLIENTE).attemptsTo(CancelarReserva.comoCliente(reserva));
    }

    @When("they try to cancel a booking that belongs to another client")
    public void cancelaLaReservaDeOtroCliente() {
        Agenda agenda = AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR));
        Actor otroCliente = theActorCalled(OTRO_CLIENTE);
        CuentasDePrueba.registrarEIniciarSesion(otroCliente, "Cliente");
        guardarReservas(List.of(reservarPorLaApi(otroCliente, agenda, 7)));
        String reservaAjena = primeraReserva();
        theActorCalled(CLIENTE).attemptsTo(CancelarReserva.comoCliente(reservaAjena));
    }

    @When("they try to cancel a booking")
    public void intentaCancelarSinSesion() {
        String reserva = reservaDeOtroNegocio();
        theActorCalled(USUARIO).attemptsTo(CancelarReserva.comoCliente(reserva));
    }

    // ---------- When (HU-26) ----------

    @When("they cancel it giving a specific reason")
    @When("its provider cancels it giving a reason")
    @When("the provider tries to cancel it")
    public void elProveedorCancelaConMotivo() {
        String reserva = primeraReserva();
        contexto().remember(MOTIVO, MOTIVO_DEL_PROVEEDOR);
        theActorCalled(PROVEEDOR).attemptsTo(CancelarReserva.comoProveedor(reserva, MOTIVO_DEL_PROVEEDOR));
    }

    /** "(only spaces)" representa un motivo hecho solo de espacios. */
    @When("they try to cancel it with the reason {string}")
    public void cancelaConElMotivo(String motivo) {
        String valor = "(only spaces)".equals(motivo) ? "   " : motivo;
        String reserva = primeraReserva();
        theActorCalled(PROVEEDOR).attemptsTo(CancelarReserva.comoProveedor(reserva, valor));
    }

    @When("the client checks their bookings")
    public void elClienteConsultaSusReservas() {
        theActorCalled(CLIENTE).attemptsTo(ConsultarReservas.propias());
    }

    @When("they try to cancel a booking that belongs to another business")
    public void cancelaUnaReservaDeOtroNegocio() {
        String reservaAjena = reservaDeOtroNegocio();
        theActorCalled(PROVEEDOR).attemptsTo(CancelarReserva.comoProveedor(reservaAjena, MOTIVO_DEL_PROVEEDOR));
    }

    @When("they try to cancel a booking using the provider cancellation")
    @When("they try to cancel a booking of a business")
    public void intentaLaCancelacionDelProveedor() {
        String reserva = reservaDeOtroNegocio();
        theActorCalled(USUARIO).attemptsTo(CancelarReserva.comoProveedor(reserva, MOTIVO_DEL_PROVEEDOR));
    }

    // ---------- Then / And ----------

    @And("they receive a message saying a booking can only be cancelled with at least 1 hour of notice")
    public void mensajeDeLaReglaDeUnaHora() {
        theActorCalled(CLIENTE).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(409)),
                seeThat(RespuestaDeLaApi.mensaje(), containsString("al menos 1 hora de antelación")));
    }

    @And("the reason given by the provider is recorded")
    public void quedaRegistradoElMotivo() {
        String motivo = contexto().recall(MOTIVO);
        Booking reserva = bookingRepository.findById(UUID.fromString(primeraReserva())).orElseThrow();
        assertThat(reserva.getCancelReason()).isEqualTo(motivo);
        assertThat(reserva.getCancelOrigin()).isEqualTo(CancelOrigin.PROVEEDOR);
    }

    @Then("they see the cancelled booking with the exact reason given by the provider")
    public void veElMotivoExacto() {
        String id = primeraReserva();
        String motivo = contexto().recall(MOTIVO);
        Actor cliente = theActorCalled(CLIENTE);
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        Map<String, Object> reserva = RespuestaDeReservas.reservasDeLaLista().answeredBy(cliente).stream()
                .filter(item -> id.equals(item.get("id"))).findFirst()
                .orElseThrow(() -> new AssertionError("El cliente no ve la reserva " + id));
        assertThat(reserva.get("status")).isEqualTo(BookingStatus.CANCELADA.name());
        assertThat(reserva.get("cancelReason")).isEqualTo(motivo);
    }

    @And("they receive a message saying the reason is required")
    public void mensajeDeMotivoObligatorio() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)),
                seeThat(RespuestaDeLaApi.mensajeDelCampo("reason"), containsString("obligatorio")));
    }

    // ---------- Apoyo ----------

    /** Crea otro negocio con una reserva confirmada de otro cliente y la deja como "la reserva" del escenario. */
    private String reservaDeOtroNegocio() {
        Agenda agenda = AgendaDePrueba.prepararNegocio(theActorCalled(OTRO_PROVEEDOR));
        Actor otroCliente = theActorCalled(OTRO_CLIENTE);
        CuentasDePrueba.registrarEIniciarSesion(otroCliente, "Cliente");
        guardarReservas(List.of(reservarPorLaApi(otroCliente, agenda, 7)));
        return primeraReserva();
    }
}
