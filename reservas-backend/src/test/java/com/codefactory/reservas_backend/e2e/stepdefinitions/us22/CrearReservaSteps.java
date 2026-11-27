package com.codefactory.reservas_backend.e2e.stepdefinitions.us22;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.models.EstadosDeReserva;
import com.codefactory.reservas_backend.e2e.models.Horario;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeHorariosYRecursos;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReservas;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarDisponibilidad;
import com.codefactory.reservas_backend.e2e.tasks.ReservarServicio;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.ServiciosDePrueba;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.AGENDA;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.HORARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CONTEXTO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.OTRO_CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * US 22: crear reserva.
 * En los escenarios negativos usa ServiciosDePrueba.cliente() para reutilizar directamente el paso
 * "the booking is rejected" de ServiciosComunesSteps.
 */
public class CrearReservaSteps {

    @Given("a Client wants to book a service with free time slots")
    public void clienteQuiereReservarServicioDisponible() {
        Agenda agenda = AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR));
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(CLIENTE), "Cliente");
        theActorCalled(CONTEXTO).remember(AGENDA, agenda);
    }

    @Given("the {string} time slot of a service is already booked")
    public void horarioYaEstaReservado(String horaInicio) {
        Agenda agenda = AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR));
        Actor cliente = theActorCalled(CLIENTE);
        CuentasDePrueba.registrarEIniciarSesion(cliente, "Cliente");

        Horario horario = new Horario(AgendaDePrueba.hoy().plusDays(7), LocalTime.parse(horaInicio));
        cliente.attemptsTo(ReservarServicio.entre(agenda.idDelServicio(), horario.fecha(),
                horario.inicioEnLaApi(), horario.finEnLaApi(agenda.duracionEnMinutos())));
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));

        Actor contexto = theActorCalled(CONTEXTO);
        contexto.remember(AGENDA, agenda);
        contexto.remember(HORARIO, horario);
    }

    @Given("a service whose resource is open from {string} to {string}")
    public void servicioConRecursoAbiertoEntre(String abre, String cierra) {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(proveedor, "Proveedor");
        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        Agenda agenda = AgendaDePrueba.montarAgenda(proveedor, AgendaDePrueba.PRECIO_POR_DEFECTO,
                "Servicio " + sufijo, "Sala " + sufijo, abre, cierra);
        theActorCalled(CONTEXTO).remember(AGENDA, agenda);
    }

    @When("they book the {string} time slot of a future date")
    public void reservaElHorarioDeUnaFechaFutura(String horaInicio) {
        Actor contexto = theActorCalled(CONTEXTO);
        Agenda agenda = contexto.recall(AGENDA);
        Horario horario = new Horario(AgendaDePrueba.hoy().plusDays(7), LocalTime.parse(horaInicio));
        contexto.remember(HORARIO, horario);

        theActorCalled(CLIENTE).attemptsTo(ReservarServicio.entre(agenda.idDelServicio(), horario.fecha(),
                horario.inicioEnLaApi(), horario.finEnLaApi(agenda.duracionEnMinutos())));
    }

    @When("another Client tries to book that time slot")
    public void otroClienteIntentaReservarEseHorario() {
        Actor contexto = theActorCalled(CONTEXTO);
        Agenda agenda = contexto.recall(AGENDA);
        Horario horario = contexto.recall(HORARIO);

        ServiciosDePrueba.cliente().attemptsTo(ReservarServicio.entre(agenda.idDelServicio(), horario.fecha(),
                horario.inicioEnLaApi(), horario.finEnLaApi(agenda.duracionEnMinutos())));
    }

    @When("a Client tries to book it from {string} to {string}")
    public void clienteIntentaReservarEnRango(String inicio, String fin) {
        Agenda agenda = theActorCalled(CONTEXTO).recall(AGENDA);
        LocalDate fecha = AgendaDePrueba.hoy().plusDays(7);
        ServiciosDePrueba.cliente().attemptsTo(ReservarServicio.entre(agenda.idDelServicio(), fecha, inicio, fin));
    }

    @Then("the booking is created with status {string}")
    public void laReservaSeCreaConEstado(String estado) {
        theActorCalled(CLIENTE).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)),
                seeThat(RespuestaDeReservas.estado(), equalTo(EstadosDeReserva.estado(estado).name())),
                seeThat(RespuestaDeHorariosYRecursos.idDeLaReserva(), not(emptyOrNullString())));
    }

    @And("that time slot is no longer available for other clients")
    public void elHorarioYaNoEstaDisponibleParaOtrosClientes() {
        Actor contexto = theActorCalled(CONTEXTO);
        Agenda agenda = contexto.recall(AGENDA);
        Horario horario = contexto.recall(HORARIO);

        Actor otroCliente = theActorCalled(OTRO_CLIENTE);
        otroCliente.attemptsTo(ConsultarDisponibilidad.delServicio(agenda.idDelServicio(), horario.fechaEnLaApi()));
        otroCliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        assertThat(RespuestaDeReservas.horariosLibres().answeredBy(otroCliente))
                .doesNotContain(horario.inicioEnLaApi());
    }

    @And("they are informed that the time slot is not available")
    public void seInformaQueElHorarioNoEstaDisponible() {
        ServiciosDePrueba.cliente().should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(409)),
                seeThat(RespuestaDeLaApi.mensaje(), containsString("no está disponible")));
    }

    @And("they are informed that the time is outside the opening hours")
    public void seInformaQueEstaFueraDelHorarioDeAtencion() {
        ServiciosDePrueba.cliente().should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(409)),
                seeThat(RespuestaDeLaApi.mensaje(), containsString("fuera del horario de atención")));
    }
}
