package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.models.EstadosDeReserva;
import com.codefactory.reservas_backend.e2e.models.Fechas;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReportes;
import com.codefactory.reservas_backend.e2e.tasks.CerrarRecurso;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarOcupacionDeRecursos;
import com.codefactory.reservas_backend.e2e.tasks.DesactivarRecurso;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.ReportesDePrueba;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.desde;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.esElPeriodoPorDefecto;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.hasta;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.negocioDeOtroProveedor;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.periodo;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.proveedorConRecurso;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.unNegocioCualquiera;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.agenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.HORAS_POR_DIA;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.HORA_DE_APERTURA;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.MINUTOS_ESPERADOS;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class OcupacionDeRecursosSteps {

    @Autowired
    private BookingRepository bookingRepository;

    @Given("a provider has logged in and their resource {string} serves from {string} to {string}")
    public void proveedorConUnRecursoConHorario(String recurso, String abre, String cierra) {
        proveedorConRecurso(recurso, abre, cierra);
    }

    @And("they have {string} with {int} % of its time booked and {string} with {int} % from {string} to {string}")
    public void recursosConOcupacion(String primero, int porcentajeDelPrimero, String segundo, int porcentajeDelSegundo,
                                     String desde, String hasta) {
        LocalDate inicio = Fechas.dia(desde);
        long dias = ChronoUnit.DAYS.between(inicio, Fechas.dia(hasta)) + 1;
        int horasPorDia = contexto().recall(HORAS_POR_DIA);
        int apertura = contexto().recall(HORA_DE_APERTURA);
        long horasDelPeriodo = dias * horasPorDia;
        Agenda agenda = agenda();
        Agenda otroRecurso = ReportesDePrueba.nuevoRecurso(theActorCalled(PROVEEDOR), agenda, segundo,
                hora(apertura), hora(apertura + horasPorDia));
        Actor cliente = theActorCalled(CLIENTE);
        ReportesDePrueba.reservasSeguidas(bookingRepository, agenda, cliente, inicio, apertura, horasPorDia,
                (int) (horasDelPeriodo * porcentajeDelPrimero / 100), BookingStatus.COMPLETADA);
        ReportesDePrueba.reservasSeguidas(bookingRepository, otroRecurso, cliente, inicio, apertura, horasPorDia,
                (int) (horasDelPeriodo * porcentajeDelSegundo / 100), BookingStatus.COMPLETADA);
    }

    // los cierres no pueden quedar en el pasado, por eso el dia es dentro de una semana
    @And("they closed the resource from {string} to {string} that same day")
    public void cerroElRecurso(String desde, String hasta) {
        LocalDate dia = AgendaDePrueba.hoy().plusDays(7);
        periodo(dia, dia);
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.attemptsTo(CerrarRecurso.entre(agenda().idDelRecurso(), dia, desde, hasta));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
    }

    @And("they have a 1 hour booking in status {string}")
    public void reservaDeUnaHoraEnEstado(String estado) {
        LocalDate dia = AgendaDePrueba.hoy().minusDays(7);
        periodo(dia, dia);
        int apertura = contexto().recall(HORA_DE_APERTURA);
        AgendaDePrueba.insertarReserva(bookingRepository, agenda(), theActorCalled(CLIENTE),
                AgendaDePrueba.a(dia, apertura), EstadosDeReserva.estado(estado));
    }

    @And("they created {string} halfway through the period they are going to check")
    public void creoUnRecursoAMitadDelPeriodo(String recurso) {
        LocalDate hoy = AgendaDePrueba.hoy();
        int horasPorDia = contexto().recall(HORAS_POR_DIA);
        int apertura = contexto().recall(HORA_DE_APERTURA);
        ReportesDePrueba.nuevoRecurso(theActorCalled(PROVEEDOR), agenda(), recurso,
                hora(apertura), hora(apertura + horasPorDia));
        periodo(hoy.minusDays(5), hoy.plusDays(4));
        // desde hoy hasta el fin del periodo son 5 dias
        contexto().remember(MINUTOS_ESPERADOS, 5L * horasPorDia * 60);
    }

    @And("their resource {string} has a schedule and no bookings in the period")
    public void recursoConHorarioSinReservas(String recurso) {
        theActorCalled(PROVEEDOR).should(seeThat("el recurso del escenario", actor -> agenda().nombreDelRecurso(),
                equalTo(recurso)));
    }

    @And("their resource {string} does not have a defined schedule")
    public void recursoSinHorario(String recurso) {
        ReportesDePrueba.nuevoRecurso(theActorCalled(PROVEEDOR), agenda(), recurso, null, null);
    }

    @And("they have {string} active and {string} deactivated")
    public void unRecursoActivoYOtroDesactivado(String activo, String desactivado) {
        Agenda otro = ReportesDePrueba.nuevoRecurso(theActorCalled(PROVEEDOR), agenda(), desactivado, "10:00", "15:00");
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.attemptsTo(DesactivarRecurso.conId(otro.idDelRecurso(), null));
        proveedor.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat("el recurso que sigue activo", actor -> agenda().nombreDelRecurso(), equalTo(activo)));
    }

    @When("they check the occupancy of their resources from {string} to {string}")
    @When("they check the occupancy of their resources with start {string} and end {string}")
    public void consultaLaOcupacionEntre(String desde, String hasta) {
        consultar(Fechas.enLaApi(desde), Fechas.enLaApi(hasta));
    }

    @When("they check the occupancy of their resources without choosing dates")
    @When("they check the occupancy of their resources")
    public void consultaLaOcupacionSinFechas() {
        consultar(null, null);
    }

    @When("they check the occupancy of that day")
    @When("they check the occupancy of that period")
    public void consultaLaOcupacionDelPeriodo() {
        consultar(desde(), hasta());
    }

    @When("they try to check the occupancy of the resources of a business that belongs to another provider")
    public void consultaLaOcupacionDeOtroNegocio() {
        String negocioAjeno = negocioDeOtroProveedor();
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarOcupacionDeRecursos.delNegocio(negocioAjeno, null, null));
    }

    @When("they try to check the occupancy of the resources of a business")
    public void intentaConsultarLaOcupacionDeUnNegocio() {
        String negocio = unNegocioCualquiera();
        theActorCalled(USUARIO).attemptsTo(ConsultarOcupacionDeRecursos.delNegocio(negocio, null, null));
    }

    @Then("they first see {string} with {int} % usage and then {string} with {int} % usage")
    public void veLosRecursosEnOrden(String primero, int porcentajeDelPrimero, String segundo, int porcentajeDelSegundo) {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.nombresDeLosRecursos(), contains(primero, segundo)),
                seeThat(RespuestaDeReportes.porcentajeDelRecurso(primero), equalTo((double) porcentajeDelPrimero)),
                seeThat(RespuestaDeReportes.porcentajeDelRecurso(segundo), equalTo((double) porcentajeDelSegundo)));
    }

    @Then("they see the usage percentage of their resources calculated over the last 30 days")
    public void veElPorcentajeDeLosUltimos30Dias() {
        String recurso = agenda().nombreDelRecurso();
        Actor proveedor = theActorCalled(PROVEEDOR);
        esElPeriodoPorDefecto(proveedor);
        proveedor.should(seeThat(RespuestaDeReportes.porcentajeDelRecurso(recurso), notNullValue()));
    }

    @Then("they see {string} calculated over the {int} hours of service that remained")
    public void veElRecursoSobreLasHorasRestantes(String recurso, int horas) {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.minutosDisponiblesDelRecurso(recurso), equalTo(horas * 60L)));
    }

    @Then("they see {string} with {string} usage")
    public void veElRecursoConElPorcentaje(String recurso, String porcentaje) {
        veElRecursoCon(recurso, Double.parseDouble(porcentaje.replace("%", "").trim()));
    }

    @Then("they see {string} with {int} % usage")
    public void veElRecursoConElPorcentajeEntero(String recurso, int porcentaje) {
        veElRecursoCon(recurso, porcentaje);
    }

    @Then("they see the percentage of {string} calculated only from the day it was created")
    public void veElPorcentajeDesdeLaCreacion(String recurso) {
        long esperados = contexto().recall(MINUTOS_ESPERADOS);
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.minutosDisponiblesDelRecurso(recurso), equalTo(esperados)));
    }

    @Then("they see {string} without a usage percentage")
    public void veElRecursoSinPorcentaje(String recurso) {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.porcentajeDelRecurso(recurso), nullValue()));
    }

    @And("they receive a message saying the resource does not have a defined schedule")
    public void mensajeDeRecursoSinHorario() {
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeLaApi.mensaje(), containsString("horario")));
    }

    @Then("they only see {string} in the result")
    public void soloVeElRecurso(String recurso) {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.nombresDeLosRecursos(), equalTo(List.of(recurso))));
    }

    private void veElRecursoCon(String recurso, double porcentaje) {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.porcentajeDelRecurso(recurso), equalTo(porcentaje)));
    }

    private static String hora(int hora) {
        return String.format("%02d:00", hora);
    }

    private static void consultar(String desde, String hasta) {
        String negocio = agenda().idDelNegocio();
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarOcupacionDeRecursos.delNegocio(negocio, desde, hasta));
    }
}
