package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.models.EstadosDeReserva;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReservas;
import com.codefactory.reservas_backend.e2e.tasks.CambiarPrecioDelServicio;
import com.codefactory.reservas_backend.e2e.tasks.CompletarReserva;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarValorDeReservas;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.agenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarAgenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarReservas;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.prepararAgendaYCliente;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.primeraReserva;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.reservarPorLaApi;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.CANTIDAD_ESPERADA;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.DESDE;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.HASTA;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.VALOR_ESPERADO;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * HU-33 - Consultar el valor de las reservas. "Reserva ejecutada" = reserva COMPLETADA. Las reservas pasadas se
 * guardan directo en la base (la API no deja reservar en el pasado); las futuras se crean por la API.
 */
public class ValorDeReservasSteps {

    @Autowired
    private BookingRepository bookingRepository;

    // ---------- Given ----------

    @Given("a provider has logged in and owns a business")
    @Given("a provider is querying the total value of their business")
    public void proveedorDuenoDeUnNegocio() {
        guardarAgenda(AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR)));
    }

    /** Tres reservas completadas dentro del periodo, con precios guardados distintos. */
    @And("their business has executed bookings between {string} and {string}")
    public void negocioConReservasEjecutadas(String desde, String hasta) {
        Actor cliente = theActorCalled(CLIENTE);
        CuentasDePrueba.registrarEIniciarSesion(cliente, "Cliente");
        Agenda agenda = agenda();
        LocalDate inicio = LocalDate.parse(desde);
        LocalDate fin = LocalDate.parse(hasta);
        List<Long> precios = List.of(30_000L, 45_000L, 25_000L);
        List<LocalDate> fechas = List.of(inicio, inicio.plusDays(1), fin);
        for (int i = 0; i < precios.size(); i++) {
            AgendaDePrueba.insertarReserva(bookingRepository, agenda, cliente, AgendaDePrueba.a(fechas.get(i), 10 + i),
                    BookingStatus.COMPLETADA, precios.get(i));
        }
        periodo(desde, hasta);
        esperado(precios.stream().mapToLong(Long::longValue).sum(), precios.size());
    }

    /** La reserva se guarda en el pasado como CONFIRMADA (con el precio de entonces) y luego se completa por la API. */
    @Given("a client booked a service when it cost {string}")
    public void clienteReservoConElPrecio(String precio) {
        long valor = EstadosDeReserva.pesos(precio);
        Agenda agenda = AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR), valor);
        guardarAgenda(agenda);
        Actor cliente = theActorCalled(CLIENTE);
        CuentasDePrueba.registrarEIniciarSesion(cliente, "Cliente");
        LocalDate fecha = AgendaDePrueba.hoy().minusDays(10);
        guardarReservas(List.of(AgendaDePrueba.insertarReserva(bookingRepository, agenda, cliente,
                AgendaDePrueba.a(fecha, 10), BookingStatus.CONFIRMADA, valor)));
        periodo(fecha.toString(), fecha.toString());
    }

    @And("the booking was already executed")
    public void laReservaYaSeEjecuto() {
        String reserva = primeraReserva();
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.attemptsTo(CompletarReserva.conId(reserva));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    @And("afterwards the provider changed the price of the service to {string}")
    public void elProveedorCambioElPrecio(String precio) {
        String servicio = agenda().idDelServicio();
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.attemptsTo(CambiarPrecioDelServicio.a(servicio, EstadosDeReserva.pesos(precio)));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    /** Dos completadas (30.000 + 45.000) y una cancelada (50.000) en un periodo pasado. */
    @Given("the queried period has executed bookings and cancelled bookings")
    public void periodoConEjecutadasYCanceladas() {
        Agenda agenda = prepararAgendaYCliente();
        Actor cliente = theActorCalled(CLIENTE);
        LocalDate hoy = AgendaDePrueba.hoy();
        AgendaDePrueba.insertarReserva(bookingRepository, agenda, cliente, AgendaDePrueba.a(hoy.minusDays(10), 9),
                BookingStatus.COMPLETADA, 30_000);
        AgendaDePrueba.insertarReserva(bookingRepository, agenda, cliente, AgendaDePrueba.a(hoy.minusDays(9), 9),
                BookingStatus.COMPLETADA, 45_000);
        AgendaDePrueba.insertarReserva(bookingRepository, agenda, cliente, AgendaDePrueba.a(hoy.minusDays(8), 9),
                BookingStatus.CANCELADA, 50_000);
        periodo(hoy.minusDays(12).toString(), hoy.minusDays(5).toString());
        esperado(75_000, 2);
    }

    @Given("the business of the provider has no bookings between {string} and {string}")
    public void negocioSinReservasEnElPeriodo(String desde, String hasta) {
        guardarAgenda(AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR)));
        periodo(desde, hasta);
    }

    /** Reservas confirmadas por la API para la próxima semana; el periodo es esa semana. */
    @Given("the provider has confirmed bookings for next week")
    public void reservasConfirmadasLaProximaSemana() {
        Agenda agenda = prepararAgendaYCliente();
        Actor cliente = theActorCalled(CLIENTE);
        reservarPorLaApi(cliente, agenda, 7);
        reservarPorLaApi(cliente, agenda, 8);
        LocalDate hoy = AgendaDePrueba.hoy();
        periodo(hoy.plusDays(7).toString(), hoy.plusDays(13).toString());
    }

    @Given("the queried period includes past days and future days")
    public void periodoConDiasPasadosYFuturos() {
        prepararAgendaYCliente();
        LocalDate hoy = AgendaDePrueba.hoy();
        periodo(hoy.minusDays(5).toString(), hoy.plusDays(5).toString());
    }

    /** Una completada hace 3 días (30.000) y una confirmada por la API dentro de 3 días (aún por realizar). */
    @And("the period has executed bookings and bookings still to happen")
    public void periodoConEjecutadasYPorRealizar() {
        Agenda agenda = agenda();
        Actor cliente = theActorCalled(CLIENTE);
        AgendaDePrueba.insertarReserva(bookingRepository, agenda, cliente,
                AgendaDePrueba.a(AgendaDePrueba.hoy().minusDays(3), 10), BookingStatus.COMPLETADA, 30_000);
        reservarPorLaApi(cliente, agenda, 3);
        esperado(30_000, 1);
    }

    // ---------- When ----------

    @When("they query the total value of the bookings of that period")
    @When("they query the total value of that period")
    @When("the provider queries the total value of that period")
    @When("the provider queries the total value of the period of that booking")
    @When("the provider queries the total value")
    public void consultaElValorDelPeriodo() {
        String negocio = agenda().idDelNegocio();
        String desde = contexto().recall(DESDE);
        String hasta = contexto().recall(HASTA);
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarValorDeReservas.delNegocio(negocio, desde, hasta));
    }

    @When("they enter {string} as the start date and {string} as the end date")
    public void ingresaLasFechas(String desde, String hasta) {
        String negocio = agenda().idDelNegocio();
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarValorDeReservas.delNegocio(negocio, desde, hasta));
    }

    @When("they try to query the total value of a business that belongs to another provider")
    public void consultaElValorDeOtroNegocio() {
        Actor otro = theActorCalled(OTRO_PROVEEDOR);
        CuentasDePrueba.registrar(otro, "Proveedor");
        String negocioAjeno = otro.recall(Memoria.ID_DE_NEGOCIO);
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarValorDeReservas.delNegocio(negocioAjeno, "2026-10-01", "2026-10-31"));
    }

    @When("they try to query the total value of the bookings of a business")
    public void intentaConsultarElValorDeUnNegocio() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrar(proveedor, "Proveedor");
        String negocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        theActorCalled(USUARIO).attemptsTo(ConsultarValorDeReservas.delNegocio(negocio, "2026-10-01", "2026-10-31"));
    }

    // ---------- Then / And ----------

    @Then("the system shows the sum of the price stored in each executed booking")
    @Then("the system adds only the executed bookings")
    @Then("the system adds only the bookings whose end time has already passed")
    public void muestraLaSumaEsperada() {
        long esperado = contexto().recall(VALOR_ESPERADO);
        elValorTotalEs(esperado);
    }

    @Then("the system adds {string} for that booking")
    @Then("the system shows a total value of {string}")
    public void elValorTotalEsDe(String valor) {
        elValorTotalEs(EstadosDeReserva.pesos(valor));
    }

    @And("it shows how many bookings the total includes")
    @And("the cancelled bookings are not included in the total or in the count")
    public void muestraLaCantidadDeReservas() {
        long esperado = contexto().recall(VALOR_ESPERADO);
        long cantidad = contexto().recall(CANTIDAD_ESPERADA);
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.should(
                seeThat(RespuestaDeReservas.valorTotal(), equalTo(esperado)),
                seeThat("la respuesta trae la cantidad de reservas incluidas en el total",
                        RespuestaDeReservas.cantidadDeReservas(), allOf(notNullValue(), equalTo(cantidad))));
    }

    @And("it reports that there were no executed bookings in the queried period")
    @And("it reports that the period has no executed bookings yet")
    public void informaQueNoHayReservasEjecutadas() {
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeLaApi.mensaje(), not(emptyOrNullString())));
    }

    @Then("the system rejects the query")
    public void rechazaLaConsulta() {
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)));
    }

    /** "Las fechas del periodo no son válidas..." o "El periodo no es válido...". */
    @And("it reports that the period is not valid")
    public void informaQueElPeriodoNoEsValido() {
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeLaApi.mensaje(),
                anyOf(containsString("no son válidas"), containsString("no es válido"))));
    }

    // ---------- Apoyo ----------

    private void elValorTotalEs(long esperado) {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReservas.valorTotal(), equalTo(esperado)));
    }

    private static void periodo(String desde, String hasta) {
        contexto().remember(DESDE, desde);
        contexto().remember(HASTA, hasta);
    }

    private static void esperado(long valor, long cantidad) {
        contexto().remember(VALOR_ESPERADO, valor);
        contexto().remember(CANTIDAD_ESPERADA, cantidad);
    }
}
