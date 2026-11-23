package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.models.EstadosDeReserva;
import com.codefactory.reservas_backend.e2e.models.Horario;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReservas;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarDisponibilidad;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.AGENDA;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.CUENTA_A_ELIMINAR;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.HORARIO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.RESERVAS;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CONTEXTO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.OTRO_CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

/**
 * Pasos que comparten las HU de reservas (HU-24 a HU-28) y de reportes (HU-33). Cada texto se define una sola vez.
 * Los datos del escenario (agenda, reservas, horario) viven en la memoria del actor CONTEXTO.
 */
public class ReservasComunesSteps {

    @Autowired
    private BookingRepository bookingRepository;

    // ---------- Given ----------

    @Given("a provider has logged in")
    public void unProveedorHaIniciadoSesion() {
        guardarAgenda(AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR)));
    }

    @Given("a user has not logged in or their session has expired")
    public void usuarioSinSesionValida() {
        theActorCalled(USUARIO).remember(Memoria.TOKEN, "");
    }

    /** Se guarda directo en la base: la API no deja reservar con menos de 1 hora (antelación mínima del negocio). */
    @Given("a client has a confirmed booking that starts in less than 1 hour")
    @Given("a confirmed booking of a business starts in less than 1 hour")
    public void reservaQueEmpiezaEnMenosDeUnaHora() {
        Agenda agenda = prepararAgendaYCliente();
        Instant enMediaHora = Instant.now().plus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.MINUTES);
        String id = AgendaDePrueba.insertarReserva(bookingRepository, agenda, theActorCalled(CLIENTE), enMediaHora,
                BookingStatus.CONFIRMADA);
        guardarReservas(List.of(id));
        contexto().remember(CUENTA_A_ELIMINAR, theActorCalled(CLIENTE).<String>recall(Memoria.ID_DE_USUARIO));
    }

    // ---------- When ----------

    @When("another client checks the availability of the service")
    public void otroClienteConsultaLaDisponibilidad() {
        Agenda agenda = agenda();
        Horario horario = contexto().recall(HORARIO);
        theActorCalled(OTRO_CLIENTE).attemptsTo(
                ConsultarDisponibilidad.delServicio(agenda.idDelServicio(), horario.fechaEnLaApi()));
    }

    // ---------- Then / And ----------

    @Then("the booking is in status {string}")
    public void laReservaQuedaEnEstado(String estado) {
        assertThat(reserva(primeraReserva()).getStatus()).isEqualTo(EstadosDeReserva.estado(estado));
    }

    @And("the cancellation origin is {string}")
    public void elOrigenDeLaCancelacionEs(String origen) {
        for (String id : reservas()) {
            assertThat(reserva(id).getCancelOrigin()).as("origen de la reserva %s", id)
                    .isEqualTo(EstadosDeReserva.origen(origen));
        }
    }

    /** 409 por la regla de la hora (HU-25) o 400 por el motivo inválido (HU-26). */
    @Then("the cancellation is rejected and the booking remains confirmed")
    public void cancelacionRechazadaYSigueConfirmada() {
        theActorInTheSpotlight().should(seeThat(RespuestaDeLaApi.codigoDeEstado(), anyOf(equalTo(400), equalTo(409))));
        assertThat(reserva(primeraReserva()).getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
    }

    @Then("the cancellation is rejected")
    public void cancelacionRechazada() {
        theActorInTheSpotlight().should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(409)));
    }

    /** "La reserva ya está cancelada" o "La reserva ya fue completada y no se puede cancelar". */
    @And("they receive a message saying the booking cannot be cancelled in its current status")
    public void mensajeDeEstadoQueNoPermiteCancelar() {
        theActorInTheSpotlight().should(seeThat(RespuestaDeLaApi.mensaje(), startsWith("La reserva ya")));
    }

    @And("the booking does not change")
    public void laReservaNoCambia() {
        Booking reserva = reserva(primeraReserva());
        assertThat(reserva.getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        assertThat(reserva.getCancelledAt()).isNull();
    }

    @Then("the cancelled time slot appears as free")
    @Then("the time slot of the cancelled booking appears as free")
    public void elHorarioApareceLibre() {
        Actor otroCliente = theActorCalled(OTRO_CLIENTE);
        otroCliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        Horario horario = contexto().recall(HORARIO);
        assertThat(RespuestaDeReservas.horariosLibres().answeredBy(otroCliente))
                .as("horarios libres del %s", horario.fechaEnLaApi()).contains(horario.inicioEnLaApi());
    }

    // ---------- Apoyo (también lo usan las demás clases de pasos de reservas) ----------

    static Actor contexto() {
        return theActorCalled(CONTEXTO);
    }

    static Agenda agenda() {
        return contexto().recall(AGENDA);
    }

    static void guardarAgenda(Agenda agenda) {
        contexto().remember(AGENDA, agenda);
    }

    static List<String> reservas() {
        return contexto().recall(RESERVAS);
    }

    static String primeraReserva() {
        return reservas().get(0);
    }

    static void guardarReservas(List<String> ids) {
        contexto().remember(RESERVAS, List.copyOf(ids));
    }

    /** Proveedor con su negocio listo y un cliente registrado con sesión. Deja la agenda en el contexto. */
    static Agenda prepararAgendaYCliente() {
        Agenda agenda = AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR));
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(CLIENTE), "Cliente");
        guardarAgenda(agenda);
        return agenda;
    }

    /** El cliente reserva por la API a "dias" días de hoy; recuerda el horario y devuelve el id. */
    static String reservarPorLaApi(Actor cliente, Agenda agenda, int dias) {
        Horario horario = AgendaDePrueba.horarioEn(dias);
        String id = AgendaDePrueba.reservar(cliente, agenda, horario);
        contexto().remember(HORARIO, horario);
        return id;
    }

    Booking reserva(String id) {
        return bookingRepository.findById(UUID.fromString(id)).orElseThrow(
                () -> new AssertionError("La reserva " + id + " ya no existe en la base"));
    }
}
