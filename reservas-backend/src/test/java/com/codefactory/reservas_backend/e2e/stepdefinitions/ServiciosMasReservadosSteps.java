package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.models.EstadosDeReserva;
import com.codefactory.reservas_backend.e2e.models.Fechas;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReportes;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarServiciosMasReservados;
import com.codefactory.reservas_backend.e2e.tasks.DesactivarServicio;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.desde;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.esElPeriodoPorDefecto;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.hasta;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.negocioDeOtroProveedor;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.periodo;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.proveedorConServicioYRecurso;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.unNegocioCualquiera;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.agenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.SERVICIOS;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.SIGUIENTE_TURNO;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

public class ServiciosMasReservadosSteps {

    private static final int HORAS_POR_DIA = 16;

    @Autowired
    private BookingRepository bookingRepository;

    @Given("a provider has logged in and offers the service {string}")
    public void proveedorQueOfreceElServicio(String servicio) {
        Agenda agenda = proveedorConServicioYRecurso(servicio, "Sala 1", "06:00", "22:00");
        Map<String, Agenda> servicios = new HashMap<>();
        servicios.put(servicio, agenda);
        contexto().remember(SERVICIOS, servicios);
        contexto().remember(SIGUIENTE_TURNO, 0);
        LocalDate hoy = AgendaDePrueba.hoy();
        periodo(hoy.minusDays(10), hoy.minusDays(3));
    }

    @And("they have {string} with {int} bookings and {string} with {int} bookings from {string} to {string}")
    public void serviciosConReservas(String primero, int reservasDelPrimero, String segundo, int reservasDelSegundo,
                                     String desde, String hasta) {
        periodo(Fechas.dia(desde), Fechas.dia(hasta));
        reservar(servicio(primero), reservasDelPrimero, BookingStatus.COMPLETADA);
        reservar(servicio(segundo), reservasDelSegundo, BookingStatus.COMPLETADA);
    }

    @And("they have a booking of the service {string} in status {string}")
    public void reservaDelServicioEnEstado(String servicio, String estado) {
        reservar(servicio(servicio), 1, EstadosDeReserva.estado(estado));
    }

    @And("they have {string} and {string} with {int} bookings each in the period")
    public void serviciosEmpatados(String primero, String segundo, int reservas) {
        reservar(servicio(primero), reservas, BookingStatus.COMPLETADA);
        reservar(servicio(segundo), reservas, BookingStatus.COMPLETADA);
    }

    // el servicio inicial recibe reservas para que haya una lista en la que "Ortodoncia" quede al final
    @And("their service {string} is active and has no bookings in the period")
    public void servicioActivoSinReservas(String servicio) {
        reservar(agenda(), 2, BookingStatus.COMPLETADA);
        servicio(servicio);
    }

    // el activo recibe una reserva para que la consulta no salga vacia por otra razon
    @And("they have {string} active and {string} deactivated without bookings in the period")
    public void unServicioActivoYOtroDesactivado(String activo, String desactivado) {
        reservar(servicio(activo), 1, BookingStatus.COMPLETADA);
        desactivar(servicio(desactivado));
    }

    @And("their service {string} was deactivated but has {int} confirmed bookings in the period")
    public void servicioDesactivadoConReservas(String servicio, int reservas) {
        Agenda agenda = servicio(servicio);
        reservar(agenda, reservas, BookingStatus.CONFIRMADA);
        desactivar(agenda);
    }

    @And("they have no confirmed or completed bookings in the period")
    public void sinReservasEnElPeriodo() {
        theActorCalled(PROVEEDOR).should(seeThat("las reservas preparadas", actor -> contexto().<Integer>recall(SIGUIENTE_TURNO),
                equalTo(0)));
    }

    @When("they check the most booked services from {string} to {string}")
    @When("they check the most booked services with start {string} and end {string}")
    public void consultaLosServiciosEntre(String desde, String hasta) {
        consultar(Fechas.enLaApi(desde), Fechas.enLaApi(hasta));
    }

    @When("they check the most booked services without choosing dates")
    public void consultaLosServiciosSinFechas() {
        consultar(null, null);
    }

    @When("they check the most booked services of that period")
    public void consultaLosServiciosDelPeriodo() {
        consultar(desde(), hasta());
    }

    @When("they try to check the most booked services of a business that belongs to another provider")
    public void consultaLosServiciosDeOtroNegocio() {
        String negocioAjeno = negocioDeOtroProveedor();
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarServiciosMasReservados.delNegocio(negocioAjeno, null, null));
    }

    @When("they try to check the most booked services of a business")
    public void intentaConsultarLosServiciosDeUnNegocio() {
        String negocio = unNegocioCualquiera();
        theActorCalled(USUARIO).attemptsTo(ConsultarServiciosMasReservados.delNegocio(negocio, null, null));
    }

    @Then("they first see {string} with {int} bookings and then {string} with {int} bookings")
    public void veLosServiciosEnOrdenConSusReservas(String primero, int reservasDelPrimero, String segundo,
                                                     int reservasDelSegundo) {
        veEnOrden(primero, segundo);
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeReportes.reservasDelServicio(primero), equalTo((long) reservasDelPrimero)),
                seeThat(RespuestaDeReportes.reservasDelServicio(segundo), equalTo((long) reservasDelSegundo)));
    }

    @Then("they first see {string} and then {string}")
    public void veEnOrden(String primero, String segundo) {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat("el primer servicio", actor -> RespuestaDeReportes.nombresDeLosServicios().answeredBy(actor).get(0),
                        equalTo(primero)),
                seeThat("el segundo servicio", actor -> RespuestaDeReportes.nombresDeLosServicios().answeredBy(actor).get(1),
                        equalTo(segundo)));
    }

    @Then("they see the number of bookings of their services calculated over the last 30 days")
    public void veLasReservasDeLosUltimos30Dias() {
        esElPeriodoPorDefecto(theActorCalled(PROVEEDOR));
    }

    @Then("they see {string} with {string} bookings")
    public void veElServicioConSusReservas(String servicio, String reservas) {
        veElServicioConReservas(servicio, Integer.parseInt(reservas));
    }

    @Then("they see {string} with {int} bookings in the result")
    public void veElServicioConReservas(String servicio, int reservas) {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.reservasDelServicio(servicio), equalTo((long) reservas)));
    }

    @Then("they see {string} with 0 bookings at the end of the list")
    public void veElServicioSinReservasAlFinal(String servicio) {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat("el ultimo servicio de la lista", actor -> {
                    List<String> nombres = RespuestaDeReportes.nombresDeLosServicios().answeredBy(actor);
                    return nombres.isEmpty() ? null : nombres.get(nombres.size() - 1);
                }, equalTo(servicio)),
                seeThat(RespuestaDeReportes.reservasDelServicio(servicio), equalTo(0L)));
    }

    @Then("they only see the service {string} in the result")
    public void soloVeElServicio(String servicio) {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.nombresDeLosServicios(), equalTo(List.of(servicio))));
    }

    @Then("they see the empty report")
    public void veElReporteVacio() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.nombresDeLosServicios(), empty()));
    }

    @And("they receive a message saying there is no data in the period")
    public void mensajeSinDatos() {
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeLaApi.mensaje(), not(emptyOrNullString())));
    }

    private Agenda servicio(String nombre) {
        Map<String, Agenda> servicios = contexto().recall(SERVICIOS);
        return servicios.computeIfAbsent(nombre,
                n -> ReportesDePrueba.nuevoServicio(theActorCalled(PROVEEDOR), agenda(), n));
    }

    private void desactivar(Agenda servicio) {
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.attemptsTo(DesactivarServicio.sinConfirmar(servicio.idDelServicio()));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    // todas van al mismo recurso: cada reserva toma el siguiente turno libre de una hora desde el inicio del periodo
    private void reservar(Agenda servicio, int cantidad, BookingStatus estado) {
        LocalDate inicio = LocalDate.parse(desde());
        int turno = contexto().recall(SIGUIENTE_TURNO);
        Actor cliente = theActorCalled(CLIENTE);
        for (int i = 0; i < cantidad; i++, turno++) {
            AgendaDePrueba.insertarReserva(bookingRepository, servicio, cliente,
                    AgendaDePrueba.a(inicio.plusDays(turno / HORAS_POR_DIA), 6 + turno % HORAS_POR_DIA), estado);
        }
        contexto().remember(SIGUIENTE_TURNO, turno);
    }

    private static void consultar(String desde, String hasta) {
        String negocio = agenda().idDelNegocio();
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarServiciosMasReservados.delNegocio(negocio, desde, hasta));
    }
}
