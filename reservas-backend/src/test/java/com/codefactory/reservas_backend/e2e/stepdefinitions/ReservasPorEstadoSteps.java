package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.models.Fechas;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReportes;
import com.codefactory.reservas_backend.e2e.tasks.CancelarReserva;
import com.codefactory.reservas_backend.e2e.tasks.CompletarReserva;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarReservasPorEstado;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.e2e.user.ReportesDePrueba;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.But;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.desde;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.hasta;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.periodo;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.agenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarAgenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.reservarPorLaApi;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.CONTEOS_ANTES;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.CONTEOS_ESPERADOS;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.RESERVAS;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class ReservasPorEstadoSteps {

    private static final List<String> ESTADOS = List.of("confirmed", "cancelled", "completed");

    @Autowired
    private BookingRepository bookingRepository;

    @Given("I am an authenticated provider on the platform")
    public void soyUnProveedorAutenticado() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(PROVEEDOR), "Proveedor");
    }

    @And("I have a registered business with active services")
    public void tengoUnNegocioConServicios() {
        guardarAgenda(agendaDe(theActorCalled(PROVEEDOR)));
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(CLIENTE), "Cliente");
        LocalDate hoy = AgendaDePrueba.hoy();
        periodo(hoy.minusDays(15), hoy.minusDays(5));
    }

    @Given("I have bookings of my business in the period {string} to {string}")
    public void reservasEnElPeriodo(String desde, String hasta) {
        LocalDate inicio = Fechas.dia(desde);
        periodo(inicio, Fechas.dia(hasta));
        insertar(agenda(), inicio, 3, 2, 4);
    }

    // el otro negocio tiene mas reservas que el mio en el mismo periodo
    @Given("there are bookings of other providers on the platform")
    public void reservasDeOtrosProveedores() {
        LocalDate inicio = LocalDate.parse(desde());
        Agenda otroNegocio = agendaDe(otroProveedorConSesion());
        insertar(agenda(), inicio, 2, 0, 1);
        Map<String, Long> mios = contexto().recall(CONTEOS_ESPERADOS);
        insertar(otroNegocio, inicio.plusDays(1), 4, 3, 5);
        contexto().remember(CONTEOS_ESPERADOS, mios);
    }

    @Given("I have no bookings in the selected period")
    public void sinReservasEnElPeriodo() {
        contexto().remember(CONTEOS_ESPERADOS, conteos(0, 0, 0));
    }

    @Given("I have confirmed and completed bookings in the selected period")
    public void reservasConfirmadasYCompletadas() {
        insertar(agenda(), LocalDate.parse(desde()), 2, 0, 3);
    }

    @But("I have no cancelled bookings")
    public void sinReservasCanceladas() {
        Map<String, Long> esperados = contexto().recall(CONTEOS_ESPERADOS);
        theActorCalled(PROVEEDOR).should(seeThat("las canceladas preparadas", actor -> esperados.get("cancelled"),
                equalTo(0L)));
    }

    // se guarda confirmada en el pasado y el proveedor la completa por la API
    @Given("a booking changed status during the period")
    public void unaReservaCambioDeEstado() {
        LocalDate dia = LocalDate.parse(desde()).plusDays(1);
        String id = AgendaDePrueba.insertarReserva(bookingRepository, agenda(), theActorCalled(CLIENTE),
                AgendaDePrueba.a(dia, 10), BookingStatus.CONFIRMADA);
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.attemptsTo(CompletarReserva.conId(id));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        contexto().remember(CONTEOS_ESPERADOS, conteos(0, 0, 1));
    }

    @Given("I checked the summary of bookings by status")
    public void consulteElResumen() {
        LocalDate hoy = AgendaDePrueba.hoy();
        periodo(hoy.plusDays(1), hoy.plusDays(7));
        Agenda agenda = agenda();
        Actor cliente = theActorCalled(CLIENTE);
        contexto().remember(RESERVAS, List.of(reservarPorLaApi(cliente, agenda, 3), reservarPorLaApi(cliente, agenda, 4)));
        consultar(desde(), hasta());
        contexto().remember(CONTEOS_ANTES, conteosDeLaRespuesta());
    }

    @Given("I am an authenticated client")
    public void soyUnClienteAutenticado() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(USUARIO), "Cliente");
    }

    @Given("I have not logged in")
    public void noHeIniciadoSesion() {
        theActorCalled(USUARIO).remember(Memoria.TOKEN, "");
    }

    @When("I select that date range in the bookings by status query")
    @When("I check the bookings by status in a period")
    @When("I check the bookings by status")
    @When("I check the same period again")
    public void consultoLasReservasPorEstado() {
        consultar(desde(), hasta());
    }

    @When("I enter a start date later than the end date")
    public void ingresoElInicioDespuesDelFin() {
        consultar("31/10/2026", "01/10/2026");
    }

    @When("I try to check the bookings by status without defining a period")
    public void consultoSinPeriodo() {
        consultar(null, null);
    }

    @When("a client cancels a booking of my business")
    public void unClienteCancelaUnaReserva() {
        List<String> reservas = contexto().recall(RESERVAS);
        Actor cliente = theActorCalled(CLIENTE);
        cliente.attemptsTo(CancelarReserva.comoCliente(reservas.get(0)));
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    @When("I try to access the bookings by status query of the business")
    @When("I try to access the bookings by status query")
    public void intentoConsultarComoUsuario() {
        String negocio = agenda().idDelNegocio();
        theActorCalled(USUARIO).attemptsTo(ConsultarReservasPorEstado.delNegocio(negocio, desde(), hasta()));
    }

    @Then("the system shows the number of {string} bookings")
    public void muestraLaCantidadDelEstado(String estado) {
        String campo = estado.toLowerCase(Locale.ROOT);
        Map<String, Long> esperados = contexto().recall(CONTEOS_ESPERADOS);
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.numero(campo), equalTo(esperados.get(campo))));
    }

    @And("the system shows the total number of bookings of the period")
    @And("the system does not show information about bookings of other providers")
    @And("the booking is not counted in more than one status at once")
    public void muestraElTotal() {
        Map<String, Long> esperados = contexto().recall(CONTEOS_ESPERADOS);
        long total = esperados.values().stream().mapToLong(Long::longValue).sum();
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeReportes.numero("total"), equalTo(total)));
    }

    @Then("the system only counts the bookings of my business")
    @Then("the system shows the corresponding amounts in the other statuses")
    @Then("the system counts the booking only in its current status")
    public void cuentaLasReservasEsperadas() {
        Map<String, Long> esperados = contexto().recall(CONTEOS_ESPERADOS);
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        for (String estado : ESTADOS) {
            proveedor.should(seeThat(RespuestaDeReportes.numero(estado), equalTo(esperados.get(estado))));
        }
    }

    @Then("the system shows zero in the {string}, {string} and {string} statuses")
    public void muestraCeroEnLosEstados(String primero, String segundo, String tercero) {
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        for (String estado : List.of(primero, segundo, tercero)) {
            proveedor.should(seeThat(RespuestaDeReportes.numero(estado.toLowerCase(Locale.ROOT)), equalTo(0L)));
        }
    }

    @Then("the system shows the value 0 in {string}")
    public void muestraCeroEn(String estado) {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.numero(estado.toLowerCase(Locale.ROOT)), equalTo(0L)));
    }

    @Then("the system shows the message {string}")
    public void muestraElMensaje(String mensaje) {
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeLaApi.mensaje(), equalTo(mensaje)));
    }

    @Then("the system says that the start date cannot be later than the end date")
    public void diceQueElInicioNoPuedeSerPosterior() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)),
                seeThat(RespuestaDeLaApi.mensaje(), containsStringIgnoringCase("la fecha de inicio no puede ser posterior")));
    }

    @And("the system does not run the query")
    @And("the system does not show results")
    public void noHayResultados() {
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeReportes.numero("total"), nullValue()));
    }

    @Then("the number of {string} bookings increases by one")
    public void laCantidadAumentaEnUno(String estado) {
        cambioEn(estado.toLowerCase(Locale.ROOT), 1);
    }

    @And("the number of the previous status decreases by one")
    public void elEstadoAnteriorDisminuyeEnUno() {
        cambioEn("confirmed", -1);
    }

    @And("the system says that they do not have permission to do it")
    public void diceQueNoTienePermisos() {
        theActorCalled(USUARIO).should(seeThat(RespuestaDeLaApi.mensaje(), containsString("permisos")));
    }

    @Then("the system redirects me to the login screen")
    public void meEnviaAlInicioDeSesion() {
        theActorCalled(USUARIO).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(401)));
    }

    private void cambioEn(String estado, long diferencia) {
        Map<String, Long> antes = contexto().recall(CONTEOS_ANTES);
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.numero(estado), equalTo(antes.get(estado) + diferencia)));
    }

    // reservas de una hora, confirmadas, canceladas y completadas en ese orden desde las 06:00 del dia indicado
    private void insertar(Agenda agenda, LocalDate dia, int confirmadas, int canceladas, int completadas) {
        Actor cliente = theActorCalled(CLIENTE);
        ReportesDePrueba.reservasSeguidas(bookingRepository, agenda, cliente, dia, 6, 4, confirmadas,
                BookingStatus.CONFIRMADA);
        ReportesDePrueba.reservasSeguidas(bookingRepository, agenda, cliente, dia, 10, 4, canceladas,
                BookingStatus.CANCELADA);
        ReportesDePrueba.reservasSeguidas(bookingRepository, agenda, cliente, dia, 14, 4, completadas,
                BookingStatus.COMPLETADA);
        contexto().remember(CONTEOS_ESPERADOS, conteos(confirmadas, canceladas, completadas));
    }

    private static Map<String, Long> conteos(long confirmadas, long canceladas, long completadas) {
        Map<String, Long> conteos = new HashMap<>();
        conteos.put("confirmed", confirmadas);
        conteos.put("cancelled", canceladas);
        conteos.put("completed", completadas);
        return conteos;
    }

    private static Map<String, Long> conteosDeLaRespuesta() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        Map<String, Long> conteos = new HashMap<>();
        for (String estado : ESTADOS) {
            conteos.put(estado, RespuestaDeReportes.numero(estado).answeredBy(proveedor));
        }
        return conteos;
    }

    private static Actor otroProveedorConSesion() {
        Actor otro = theActorCalled(OTRO_PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(otro, "Proveedor");
        return otro;
    }

    private static Agenda agendaDe(Actor proveedor) {
        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        return AgendaDePrueba.montarAgenda(proveedor, AgendaDePrueba.PRECIO_POR_DEFECTO, "Servicio " + sufijo,
                "Sala " + sufijo, "06:00", "22:00");
    }

    private static void consultar(String desde, String hasta) {
        String negocio = agenda().idDelNegocio();
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarReservasPorEstado.delNegocio(negocio,
                desde == null ? null : Fechas.enLaApi(desde), hasta == null ? null : Fechas.enLaApi(hasta)));
    }
}
