package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.models.EstadosDeReserva;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReservas;
import com.codefactory.reservas_backend.e2e.tasks.CompletarReserva;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import io.cucumber.java.PendingException;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.agenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarReservas;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.prepararAgendaYCliente;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.primeraReserva;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.ESTADO_PEDIDO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * HU-27 - Completar reserva. Una reserva solo se completa cuando ya pasó su hora de fin, y la API no deja
 * reservar en el pasado: la reserva se guarda directo en la base con un horario que ya terminó.
 */
public class CompletarReservaSteps {

    @Autowired
    private BookingRepository bookingRepository;

    @Given("the provider has a booking in status {string}")
    public void elProveedorTieneUnaReservaEnEstado(String estado) {
        prepararAgendaYCliente();
        contexto().remember(ESTADO_PEDIDO, estado);
    }

    @And("the date and time of the service have already passed")
    public void elHorarioYaPaso() {
        Agenda agenda = agenda();
        String estado = contexto().recall(ESTADO_PEDIDO);
        Instant haceTresHoras = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        guardarReservas(List.of(AgendaDePrueba.insertarReserva(bookingRepository, agenda, theActorCalled(CLIENTE),
                haceTresHoras, EstadosDeReserva.estado(estado))));
    }

    /** El estado del texto es el que se espera; la acción es la misma (POST .../completion). */
    @When("the provider marks the booking as {string}")
    public void elProveedorMarcaLaReserva(String estado) {
        String reserva = primeraReserva();
        theActorCalled(PROVEEDOR).attemptsTo(CompletarReserva.conId(reserva));
    }

    @Then("the status of the booking is updated to {string}")
    public void elEstadoSeActualiza(String estado) {
        BookingStatus esperado = EstadosDeReserva.estado(estado);
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReservas.estado(), equalTo(esperado.name())));
        assertThat(bookingRepository.findById(UUID.fromString(primeraReserva())).orElseThrow().getStatus())
                .isEqualTo(esperado);
    }

    /** No existe calificación ni reseña (fuera de alcance, supuesto P4 / D5): el escenario está como @pendiente. */
    @Then("the option for the client to leave a rating or review is enabled")
    public void seHabilitaLaCalificacion() {
        throw new PendingException("La calificación o reseña está fuera del alcance (Calidad, 2026-11-06, supuesto P4)");
    }
}
