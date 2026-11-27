package com.codefactory.reservas_backend.e2e.stepdefinitions.us20;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReservas;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarDisponibilidad;
import com.codefactory.reservas_backend.e2e.tasks.DefinirHorario;
import com.codefactory.reservas_backend.e2e.tasks.ReservarServicio;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.user.MemoriaDeHorarios.FECHA;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.AGENDA;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CONTEXTO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.OTRO_CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/** US 20: consultar disponibilidad de un servicio. */
public class ConsultarDisponibilidadSteps {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    @Given("a service has opening hours and no bookings on a future date")
    public void servicioConHorarioSinReservasEnFechaFutura() {
        Agenda agenda = prepararServicioConHorario("09:00", "12:00");
        LocalDate fecha = AgendaDePrueba.hoy().plusDays(7);
        Actor contexto = theActorCalled(CONTEXTO);
        contexto.remember(AGENDA, agenda);
        contexto.remember(FECHA, fecha);
    }

    @Given("a service has a booking at {string} on a future date")
    public void servicioConReservaEnUnaHoraDeFechaFutura(String horaInicio) {
        Agenda agenda = prepararServicioConHorario("09:00", "12:00");
        LocalDate fecha = AgendaDePrueba.hoy().plusDays(7);
        String horaFin = LocalTime.parse(horaInicio).plusMinutes(agenda.duracionEnMinutos()).format(HORA);

        Actor otroCliente = theActorCalled(OTRO_CLIENTE);
        CuentasDePrueba.registrarEIniciarSesion(otroCliente, "Cliente");
        otroCliente.attemptsTo(ReservarServicio.entre(agenda.idDelServicio(), fecha, horaInicio, horaFin));
        otroCliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));

        Actor contexto = theActorCalled(CONTEXTO);
        contexto.remember(AGENDA, agenda);
        contexto.remember(FECHA, fecha);
    }

    @Given("a service has no opening hours on Sundays")
    public void servicioSinHorarioLosDomingos() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        Agenda agenda = prepararServicioConHorario("09:00", "12:00");
        proveedor.attemptsTo(DefinirHorario.deLunesASabado(agenda.idDelRecurso(), "09:00", "12:00"));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        theActorCalled(CONTEXTO).remember(AGENDA, agenda);
    }

    @When("a Client checks its availability for that date")
    public void clienteConsultaDisponibilidadParaEsaFecha() {
        Actor contexto = theActorCalled(CONTEXTO);
        Agenda agenda = contexto.recall(AGENDA);
        LocalDate fecha = contexto.recall(FECHA);
        theActorCalled(CLIENTE).attemptsTo(ConsultarDisponibilidad.delServicio(agenda.idDelServicio(), fecha.toString()));
    }

    @When("a Client checks its availability for next Sunday")
    public void clienteConsultaDisponibilidadParaElProximoDomingo() {
        Agenda agenda = theActorCalled(CONTEXTO).recall(AGENDA);
        LocalDate proximoDomingo = AgendaDePrueba.hoy().with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        theActorCalled(CLIENTE).attemptsTo(
                ConsultarDisponibilidad.delServicio(agenda.idDelServicio(), proximoDomingo.toString()));
    }

    @Then("they see every time slot defined for that day")
    public void veTodosLosHorariosDefinidosParaEseDia() {
        Actor cliente = theActorCalled(CLIENTE);
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        List<String> horarios = RespuestaDeReservas.horariosLibres().answeredBy(cliente);
        assertThat(horarios).containsExactly("09:00", "10:00", "11:00");
    }

    @Then("the {string} time slot is not shown as free")
    public void elHorarioReservadoNoApareceLibre(String horaReservada) {
        Actor cliente = theActorCalled(CLIENTE);
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        List<String> horarios = RespuestaDeReservas.horariosLibres().answeredBy(cliente);
        assertThat(horarios).doesNotContain(horaReservada).contains("09:00", "11:00");
    }

    @Then("they are informed that there are no time slots available that day")
    public void seInformaQueNoHayHorariosDisponibles() {
        Actor cliente = theActorCalled(CLIENTE);
        cliente.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeLaApi.mensaje(), containsString("No hay horarios disponibles ese día")));
        assertThat(RespuestaDeReservas.horariosLibres().answeredBy(cliente)).isEmpty();
    }

    private static Agenda prepararServicioConHorario(String abre, String cierra) {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(proveedor, "Proveedor");
        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        return AgendaDePrueba.montarAgenda(proveedor, AgendaDePrueba.PRECIO_POR_DEFECTO,
                "Servicio " + sufijo, "Sala " + sufijo, abre, cierra);
    }
}
