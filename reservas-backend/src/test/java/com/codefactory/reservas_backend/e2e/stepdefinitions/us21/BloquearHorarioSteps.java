package com.codefactory.reservas_backend.e2e.stepdefinitions.us21;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeHorariosYRecursos;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReservas;
import com.codefactory.reservas_backend.e2e.tasks.CerrarRecurso;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarDisponibilidad;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarHorarioDelRecurso;
import com.codefactory.reservas_backend.e2e.tasks.ReservarServicio;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.user.MemoriaDeHorarios.FECHA;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeHorarios.FIN_CIERRE;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeHorarios.HORARIO_ANTERIOR;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeHorarios.INICIO_CIERRE;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.AGENDA;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.RESERVAS;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CONTEXTO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * US 21: bloquear un horario puntual de un recurso.
 * Reutiliza el Given de US 19 ("a Provider owns a resource with weekly opening hours") y el Then de
 * ReservasComunesSteps ("the booking is in status {string}").
 */
public class BloquearHorarioSteps {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private BookingRepository bookingRepository;

    @Given("a resource has a confirmed booking on a future date")
    public void recursoConReservaConfirmadaEnFechaFutura() {
        Agenda agenda = AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR));
        Actor cliente = theActorCalled(CLIENTE);
        CuentasDePrueba.registrarEIniciarSesion(cliente, "Cliente");

        LocalDate fecha = AgendaDePrueba.hoy().plusDays(8);
        cliente.attemptsTo(ReservarServicio.entre(agenda.idDelServicio(), fecha, "10:00", "11:00"));
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        String idReserva = RespuestaDeHorariosYRecursos.idDeLaReserva().answeredBy(cliente);

        Actor contexto = theActorCalled(CONTEXTO);
        contexto.remember(AGENDA, agenda);
        contexto.remember(FECHA, fecha);
        contexto.remember(RESERVAS, List.of(idReserva));
    }

    @When("they close the resource from {string} to {string} on a future date")
    public void cierraElRecursoEnUnRangoDeFechaFutura(String inicio, String fin) {
        Actor contexto = theActorCalled(CONTEXTO);
        Agenda agenda = contexto.recall(AGENDA);
        LocalDate fecha = AgendaDePrueba.hoy().plusDays(7);
        contexto.remember(FECHA, fecha);
        contexto.remember(INICIO_CIERRE, inicio);
        contexto.remember(FIN_CIERRE, fin);

        theActorCalled(PROVEEDOR).attemptsTo(CerrarRecurso.entre(agenda.idDelRecurso(), fecha, inicio, fin));
    }

    @When("the Provider tries to close the resource on that date without confirming")
    public void intentaCerrarSinConfirmar() {
        Actor contexto = theActorCalled(CONTEXTO);
        Agenda agenda = contexto.recall(AGENDA);
        LocalDate fecha = contexto.recall(FECHA);
        theActorCalled(PROVEEDOR).attemptsTo(CerrarRecurso.todoElDia(agenda.idDelRecurso(), fecha));
    }

    @When("the Provider closes the resource on that date and confirms the action")
    public void cierraYConfirma() {
        Actor contexto = theActorCalled(CONTEXTO);
        Agenda agenda = contexto.recall(AGENDA);
        LocalDate fecha = contexto.recall(FECHA);
        theActorCalled(PROVEEDOR).attemptsTo(CerrarRecurso.todoElDiaConfirmando(agenda.idDelRecurso(), fecha));
    }

    @Then("the time slots in that period are no longer available")
    public void losHorariosDeEsePeriodoYaNoEstanDisponibles() {
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));

        Actor contexto = theActorCalled(CONTEXTO);
        Agenda agenda = contexto.recall(AGENDA);
        LocalDate fecha = contexto.recall(FECHA);
        LocalTime inicio = LocalTime.parse(contexto.recall(INICIO_CIERRE));
        LocalTime fin = LocalTime.parse(contexto.recall(FIN_CIERRE));

        Actor cliente = theActorCalled(CLIENTE);
        cliente.attemptsTo(ConsultarDisponibilidad.delServicio(agenda.idDelServicio(), fecha.toString()));
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));

        List<String> libres = RespuestaDeReservas.horariosLibres().answeredBy(cliente);
        for (LocalTime hora = inicio; hora.isBefore(fin); hora = hora.plusMinutes(agenda.duracionEnMinutos())) {
            assertThat(libres).doesNotContain(hora.format(HORA));
        }
        assertThat(libres).contains("10:00");
    }

    @And("the weekly opening hours of the resource do not change")
    public void elHorarioSemanalNoCambia() {
        Actor contexto = theActorCalled(CONTEXTO);
        Agenda agenda = contexto.recall(AGENDA);
        Map<Integer, List<Map<String, String>>> anterior = contexto.recall(HORARIO_ANTERIOR);

        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.attemptsTo(ConsultarHorarioDelRecurso.delRecurso(agenda.idDelRecurso()));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        assertThat(RespuestaDeHorariosYRecursos.horarioSemanal().answeredBy(proveedor)).isEqualTo(anterior);
    }

    @Then("they are informed of how many bookings will be cancelled")
    public void seInformaCuantasReservasSeCancelaran() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(409)),
                seeThat(RespuestaDeLaApi.codigoDeError(), equalTo("CONFIRMATION_REQUIRED")),
                seeThat(RespuestaDeHorariosYRecursos.reservasAfectadas(), equalTo("1")));
    }

    @And("the booking remains confirmed")
    public void laReservaPermaneceConfirmada() {
        List<String> reservas = theActorCalled(CONTEXTO).recall(RESERVAS);
        BookingStatus estado = bookingRepository.findById(UUID.fromString(reservas.get(0)))
                .orElseThrow().getStatus();
        assertThat(estado).isEqualTo(BookingStatus.CONFIRMADA);
    }

    @Then("the closure is saved")
    public void elCierreQuedaGuardado() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)),
                seeThat(RespuestaDeHorariosYRecursos.idDelCierre(), not(emptyOrNullString())));
    }
}
