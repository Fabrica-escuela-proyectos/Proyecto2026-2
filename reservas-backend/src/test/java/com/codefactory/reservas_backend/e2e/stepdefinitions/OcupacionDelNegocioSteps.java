package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReportes;
import com.codefactory.reservas_backend.e2e.tasks.CerrarRecurso;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarOcupacionDelNegocio;
import com.codefactory.reservas_backend.e2e.tasks.DesactivarRecurso;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.e2e.user.ReportesDePrueba;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.desde;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.hasta;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.negocioDeOtroProveedor;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.periodo;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.proveedorConRecurso;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.unNegocioCualquiera;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.agenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarAgenda;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.MINUTOS_ESPERADOS;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.NEGOCIO_AJENO;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class OcupacionDelNegocioSteps {

    // un recurso de 06:00 a 22:00 da 960 minutos al dia
    private static final String ABRE = "06:00";
    private static final String CIERRA = "22:00";
    private static final long MINUTOS_DEL_DIA = 960;

    @Autowired
    private BookingRepository bookingRepository;

    @Given("the business has active resources with a schedule that day")
    @Given("the provider has active resources with a current schedule that day")
    @Given("the provider is authenticated and has an active business")
    @Given("the provider is authenticated")
    public void negocioConRecursosConHorario() {
        proveedorConRecurso("Sala 1", ABRE, CIERRA);
        LocalDate dia = AgendaDePrueba.hoy().minusDays(3);
        periodo(dia, dia);
    }

    // 2 completadas y 1 confirmada de una hora: 180 de 960 minutos = 18.75 %, que se muestra como 18.8
    @And("there are confirmed or completed bookings that take up minutes")
    public void reservasQueOcupanMinutos() {
        LocalDate dia = LocalDate.parse(desde());
        Agenda agenda = agenda();
        Actor cliente = theActorCalled(CLIENTE);
        ReportesDePrueba.reservasSeguidas(bookingRepository, agenda, cliente, dia, 6, 16, 2, BookingStatus.COMPLETADA);
        AgendaDePrueba.insertarReserva(bookingRepository, agenda, cliente, AgendaDePrueba.a(dia, 8),
                BookingStatus.CONFIRMADA);
        AgendaDePrueba.insertarReserva(bookingRepository, agenda, cliente, AgendaDePrueba.a(dia, 9),
                BookingStatus.CANCELADA);
        contexto().remember(MINUTOS_ESPERADOS, 180L);
    }

    @And("there are no confirmed or completed bookings that day")
    public void sinReservasEseDia() {
        AgendaDePrueba.insertarReserva(bookingRepository, agenda(), theActorCalled(CLIENTE),
                AgendaDePrueba.a(LocalDate.parse(desde()), 10), BookingStatus.CANCELADA);
    }

    // un cierre de todo el dia deja el horario sin minutos; los cierres solo pueden ser a futuro
    @Given("the schedule of that day has no available minutes")
    public void diaSinMinutosDisponibles() {
        Agenda agenda = proveedorConRecurso("Sala 1", ABRE, CIERRA);
        LocalDate dia = AgendaDePrueba.hoy().plusDays(10);
        periodo(dia, dia);
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.attemptsTo(CerrarRecurso.todoElDia(agenda.idDelRecurso(), dia));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
    }

    @Given("the business has one active resource and one inactive resource")
    public void unRecursoActivoYOtroInactivo() {
        negocioConRecursosConHorario();
        Agenda inactivo = ReportesDePrueba.nuevoRecurso(theActorCalled(PROVEEDOR), agenda(), "Sala 2", ABRE, CIERRA);
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.attemptsTo(DesactivarRecurso.conId(inactivo.idDelRecurso(), null));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    // proveedor recien registrado: tiene negocio pero ningun recurso
    @Given("there are no bookings or resources with a schedule in the queried period")
    public void periodoSinDatos() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(proveedor, "Proveedor");
        String negocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        guardarAgenda(new Agenda(negocio, null, null, null, null, null, 0, 0));
        LocalDate hoy = AgendaDePrueba.hoy();
        periodo(hoy.minusDays(6), hoy);
    }

    @Given("there is a business that belongs to another provider")
    public void negocioDeOtroProveedorExiste() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(PROVEEDOR), "Proveedor");
        String negocioAjeno = negocioDeOtroProveedor();
        contexto().remember(NEGOCIO_AJENO, negocioAjeno);
    }

    @When("the provider checks the occupancy of that day")
    @When("the provider checks the occupancy of that period")
    @When("they request the occupancy of a specific day")
    public void consultaLaOcupacionDelPeriodo() {
        consultarPorDia(desde(), hasta());
    }

    @When("they request the occupancy of a specific week")
    public void consultaLaOcupacionDeUnaSemana() {
        LocalDate lunes = semanaPasada();
        periodo(lunes, lunes.plusDays(6));
        consultarPorDia(desde(), hasta());
    }

    @When("they request the occupancy of a specific week grouped by week")
    public void consultaLaOcupacionAgrupadaPorSemana() {
        LocalDate lunes = semanaPasada();
        periodo(lunes, lunes.plusDays(6));
        String negocio = agenda().idDelNegocio();
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarOcupacionDelNegocio.porSemana(negocio, desde(), hasta()));
    }

    @When("they check the occupancy with a start date after the end date")
    public void consultaConElInicioDespuesDelFin() {
        LocalDate hoy = AgendaDePrueba.hoy();
        consultarPorDia(hoy.toString(), hoy.minusDays(1).toString());
    }

    @When("the provider tries to check its occupancy")
    public void consultaLaOcupacionDelNegocioAjeno() {
        String negocioAjeno = contexto().recall(NEGOCIO_AJENO);
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarOcupacionDelNegocio.porDia(negocioAjeno, null, null));
    }

    @When("they try to check the occupancy of a business")
    public void intentaConsultarLaOcupacionDeUnNegocio() {
        String negocio = unNegocioCualquiera();
        theActorCalled(USUARIO).attemptsTo(ConsultarOcupacionDelNegocio.porDia(negocio, null, null));
    }

    @Then("the result is the booked minutes divided by the available minutes times 100")
    public void elResultadoEsMinutosReservadosSobreDisponibles() {
        long reservados = contexto().recall(MINUTOS_ESPERADOS);
        double esperado = BigDecimal.valueOf(reservados * 100)
                .divide(BigDecimal.valueOf(MINUTOS_DEL_DIA), 1, RoundingMode.HALF_UP).doubleValue();
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.numero("periods[0].bookedMinutes"), equalTo(reservados)),
                seeThat(RespuestaDeReportes.numero("periods[0].availableMinutes"), equalTo(MINUTOS_DEL_DIA)),
                seeThat(RespuestaDeReportes.porcentajeGeneral(), equalTo(esperado)));
    }

    @And("the percentage is shown with one decimal")
    public void elPorcentajeTieneUnDecimal() {
        theActorCalled(PROVEEDOR).should(seeThat("los decimales del porcentaje",
                actor -> BigDecimal.valueOf(RespuestaDeReportes.porcentajeGeneral().answeredBy(actor)).scale(),
                lessThanOrEqualTo(1)));
    }

    @Then("the system returns no occupancy percentage")
    public void sinPorcentajeDeOcupacion() {
        Map<String, Object> dia = primerPeriodo();
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeReportes.porcentajeGeneral(), nullValue()),
                seeThat("el porcentaje del dia", actor -> dia.get("occupancyPercent"), nullValue()));
    }

    @And("it does not show 0 % but indicates that there is no data available")
    public void indicaQueNoHayDatos() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeReportes.porcentajeGeneral(), not(equalTo(0.0))),
                seeThat(RespuestaDeLaApi.mensaje(), not(emptyOrNullString())));
    }

    @Then("the system shows 0 %")
    public void muestraCeroPorCiento() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.porcentajeGeneral(), equalTo(0.0)));
    }

    @Then("only the available minutes of the active resource are taken into account")
    public void soloCuentaElRecursoActivo() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.numero("periods[0].availableMinutes"), equalTo(MINUTOS_DEL_DIA)));
    }

    @Then("they receive the occupancy percentage of that day")
    public void recibeElPorcentajeDelDia() {
        String dia = desde();
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeReportes.periodos(), hasSize(1)),
                seeThat(RespuestaDeReportes.texto("periods[0].start"), equalTo(dia)),
                seeThat(RespuestaDeReportes.porcentajeGeneral(), notNullValue()));
    }

    @Then("they receive the occupancy percentage for each day of that week")
    public void recibeElPorcentajeDeCadaDia() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeReportes.periodos(), hasSize(7)),
                seeThat("los porcentajes de cada dia", OcupacionDelNegocioSteps::porcentajesDeCadaDia,
                        everyItem(notNullValue())));
    }

    @Then("they receive one occupancy percentage from Monday to Sunday")
    public void recibeUnPorcentajeSemanal() {
        String lunes = desde();
        String domingo = hasta();
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeReportes.texto("groupBy"), equalTo("WEEK")),
                seeThat(RespuestaDeReportes.periodos(), hasSize(1)),
                seeThat(RespuestaDeReportes.texto("periods[0].start"), equalTo(lunes)),
                seeThat(RespuestaDeReportes.texto("periods[0].end"), equalTo(domingo)),
                seeThat(RespuestaDeReportes.porcentajeGeneral(), notNullValue()));
    }

    @Then("the system rejects the request with an invalid period error message")
    public void rechazaPorPeriodoInvalido() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)),
                seeThat(RespuestaDeLaApi.mensaje(), containsString("no es válido")));
    }

    @Then("the system returns the empty report with a message saying there is no data")
    public void reporteVacioConMensaje() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.porcentajeGeneral(), nullValue()),
                seeThat("los porcentajes de cada dia", OcupacionDelNegocioSteps::porcentajesDeCadaDia,
                        everyItem(nullValue())),
                seeThat(RespuestaDeLaApi.mensaje(), not(emptyOrNullString())));
    }

    @Then("the system rejects the request with an unauthorized access error")
    public void rechazaPorAccesoNoAutorizado() {
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(403)));
    }

    private static LocalDate semanaPasada() {
        return AgendaDePrueba.hoy().minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static List<Object> porcentajesDeCadaDia(Actor actor) {
        return RespuestaDeReportes.periodos().answeredBy(actor).stream().map(p -> p.get("occupancyPercent")).toList();
    }

    private static Map<String, Object> primerPeriodo() {
        List<Map<String, Object>> periodos = RespuestaDeReportes.periodos().answeredBy(theActorCalled(PROVEEDOR));
        return periodos.get(0);
    }

    private static void consultarPorDia(String desde, String hasta) {
        String negocio = agenda().idDelNegocio();
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarOcupacionDelNegocio.porDia(negocio, desde, hasta));
    }
}
