package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReportes;
import com.codefactory.reservas_backend.e2e.tasks.AutenticarseComoAdministrador;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarResumenDeLaPlataforma;
import com.codefactory.reservas_backend.e2e.tasks.IniciarSesion;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.e2e.user.ReportesDePrueba;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import com.codefactory.reservas_backend.provider.domain.Provider;
import com.codefactory.reservas_backend.provider.infrastructure.ProviderRepository;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.desde;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.hasta;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.periodo;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReportesComunesSteps.proveedorConRecurso;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.MINUTOS_ESPERADOS;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.NEGOCIOS_ANTES;
import static com.codefactory.reservas_backend.e2e.user.Personajes.ADMINISTRADOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class ResumenDeLaPlataformaSteps {

    private static final String SIN_VALOR = "(empty)";

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ProviderRepository providerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Given("an authenticated Administrator wants to supervise the platform")
    @Given("an authenticated Administrator wants to check a single day")
    @Given("an authenticated Administrator wants to check the summary")
    public void administradorAutenticado() {
        User administrador = CuentasDePrueba.crearAdministrador(userRepository, roleRepository, passwordEncoder);
        theActorCalled(ADMINISTRADOR).attemptsTo(
                AutenticarseComoAdministrador.conVerificacion(CuentasDePrueba.credencialesDe(administrador)));
        LocalDate dia = ReportesDePrueba.diaAislado();
        periodo(dia, dia);
    }

    @And("the platform has confirmed, cancelled and completed bookings in a period")
    public void reservasEnLaPlataforma() {
        insertarReservas(2, 1, 3);
    }

    @And("there are no bookings in the indicated period")
    public void sinReservasEnElPeriodo() {
        proveedorConRecurso("Sala 1", "06:00", "22:00");
    }

    @And("there are cancelled bookings in the period")
    public void reservasCanceladasEnElPeriodo() {
        insertarReservas(1, 2, 0);
    }

    // se toma el conteo antes de deshabilitar la cuenta, porque el resumen incluye los negocios de otros escenarios
    @And("the Provider of a business has a disabled account")
    public void proveedorConLaCuentaDeshabilitada() {
        proveedorConRecurso("Sala 1", "06:00", "22:00");
        String idDelProveedor = theActorCalled(PROVEEDOR).recall(Memoria.ID_DE_USUARIO);
        consultar(desde(), hasta());
        Actor administrador = theActorCalled(ADMINISTRADOR);
        administrador.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        contexto().remember(NEGOCIOS_ANTES, RespuestaDeReportes.numero("activeBusinesses").answeredBy(administrador));
        deshabilitar(UUID.fromString(idDelProveedor));
    }

    @And("the platform has no active businesses")
    public void sinNegociosActivos() {
        proveedorConRecurso("Sala 1", "06:00", "22:00");
        providerRepository.findAll().stream().map(Provider::getUserId).forEach(this::deshabilitar);
    }

    @Given("an Administrator has not enabled two-step authentication")
    public void administradorSinMfa() {
        User administrador = CuentasDePrueba.crearAdministrador(userRepository, roleRepository, passwordEncoder);
        Actor usuario = theActorCalled(USUARIO);
        usuario.attemptsTo(IniciarSesion.con(CuentasDePrueba.credencialesDe(administrador)));
        usuario.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    @When("they check the summary of a valid period")
    @When("the Administrator checks the summary")
    @When("they enter the same date as the start and the end")
    public void consultaElResumen() {
        consultar(desde(), hasta());
    }

    @When("they enter a start date later than the end date")
    public void ingresaElInicioDespuesDelFin() {
        consultar("2026-10-31", "2026-10-01");
    }

    @When("they enter {string} as the start and {string} as the end of the summary")
    public void ingresaLasFechas(String desde, String hasta) {
        consultar(SIN_VALOR.equals(desde) ? null : desde, SIN_VALOR.equals(hasta) ? null : hasta);
    }

    @When("they try to check the platform summary")
    public void intentaConsultarElResumen() {
        theActorCalled(USUARIO).attemptsTo(ConsultarResumenDeLaPlataforma.delPeriodo(null, null));
    }

    @Then("the system shows the number of active businesses")
    public void muestraLosNegociosActivos() {
        theActorCalled(ADMINISTRADOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.numero("activeBusinesses"), greaterThanOrEqualTo(1L)));
    }

    @And("it shows the confirmed, cancelled and completed bookings")
    public void muestraLasReservasPorEstado() {
        conteos(2, 1, 3);
    }

    @And("it shows the overall occupancy as a percentage")
    public void muestraLaOcupacionGeneral() {
        long minutos = contexto().recall(MINUTOS_ESPERADOS);
        theActorCalled(ADMINISTRADOR).should(
                seeThat(RespuestaDeReportes.numero("bookedMinutes"), equalTo(minutos)),
                seeThat(RespuestaDeReportes.porcentajeGeneral(),
                        allOf(notNullValue(), greaterThanOrEqualTo(0.0), lessThanOrEqualTo(100.0))));
    }

    @Then("the system shows the summary of that day")
    public void muestraElResumenDelDia() {
        String dia = desde();
        theActorCalled(ADMINISTRADOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.texto("from"), equalTo(dia)),
                seeThat(RespuestaDeReportes.texto("to"), equalTo(dia)),
                seeThat(RespuestaDeReportes.numero("activeBusinesses"), notNullValue()));
    }

    @Then("the system shows the bookings at zero and the occupancy at 0 %")
    public void reservasEnCeroYOcupacionEnCero() {
        conteos(0, 0, 0);
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeReportes.porcentajeGeneral(), equalTo(0.0)));
    }

    @And("it reports that there are no bookings in the period")
    public void informaQueNoHayReservas() {
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeLaApi.mensaje(), not(emptyOrNullString())));
    }

    @Then("the system shows 0 active businesses")
    public void ceroNegociosActivos() {
        theActorCalled(ADMINISTRADOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.numero("activeBusinesses"), equalTo(0L)));
    }

    @And("it does not calculate an occupancy percentage")
    public void sinPorcentajeDeOcupacion() {
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeReportes.porcentajeGeneral(), nullValue()));
    }

    @And("it reports that there are no available hours")
    public void informaQueNoHayHorasDisponibles() {
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeLaApi.mensaje(), containsString("horario")));
    }

    @Then("the cancelled bookings are counted in their status")
    public void lasCanceladasSeCuentanEnSuEstado() {
        theActorCalled(ADMINISTRADOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.numero("cancelled"), equalTo(2L)));
    }

    @And("they are not added to the overall occupancy")
    public void noSumanALaOcupacion() {
        long minutos = contexto().recall(MINUTOS_ESPERADOS);
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeReportes.numero("bookedMinutes"), equalTo(minutos)));
    }

    @Then("that business is not included among the active businesses")
    public void elNegocioNoCuentaComoActivo() {
        long antes = contexto().recall(NEGOCIOS_ANTES);
        theActorCalled(ADMINISTRADOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.numero("activeBusinesses"), equalTo(antes - 1)));
    }

    @Then("the system does not calculate the summary")
    public void noCalculaElResumen() {
        theActorCalled(ADMINISTRADOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)),
                seeThat(RespuestaDeReportes.numero("activeBusinesses"), nullValue()));
    }

    @And("it reports that the period is invalid")
    public void informaQueElPeriodoNoEsValido() {
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeLaApi.mensaje(),
                anyOf(containsString("no son válidas"), containsString("no es válido"))));
    }

    @And("it reports that the {string} date is missing")
    public void informaQueFaltaLaFecha(String fecha) {
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeLaApi.mensaje(), containsString(fecha)));
    }

    @And("no data of the platform is shown")
    public void noMuestraDatos() {
        theActorCalled(USUARIO).should(seeThat(RespuestaDeReportes.numero("activeBusinesses"), nullValue()));
    }

    @And("they are told that they must enable MFA first")
    public void debeActivarElMfa() {
        theActorCalled(USUARIO).should(seeThat(RespuestaDeLaApi.codigoDeError(), equalTo("MFA_ENROLLMENT_REQUIRED")));
    }

    // reservas de una hora en el dia aislado del escenario; los minutos ocupados son las no canceladas
    private void insertarReservas(int confirmadas, int canceladas, int completadas) {
        Agenda agenda = proveedorConRecurso("Sala 1", "06:00", "22:00");
        LocalDate dia = LocalDate.parse(desde());
        Actor cliente = theActorCalled(CLIENTE);
        ReportesDePrueba.reservasSeguidas(bookingRepository, agenda, cliente, dia, 6, 4, confirmadas,
                BookingStatus.CONFIRMADA);
        ReportesDePrueba.reservasSeguidas(bookingRepository, agenda, cliente, dia, 10, 4, canceladas,
                BookingStatus.CANCELADA);
        ReportesDePrueba.reservasSeguidas(bookingRepository, agenda, cliente, dia, 14, 4, completadas,
                BookingStatus.COMPLETADA);
        contexto().remember(MINUTOS_ESPERADOS, (long) (confirmadas + completadas) * AgendaDePrueba.DURACION);
    }

    private void conteos(long confirmadas, long canceladas, long completadas) {
        theActorCalled(ADMINISTRADOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.numero("confirmed"), equalTo(confirmadas)),
                seeThat(RespuestaDeReportes.numero("cancelled"), equalTo(canceladas)),
                seeThat(RespuestaDeReportes.numero("completed"), equalTo(completadas)));
    }

    private void deshabilitar(UUID idDelUsuario) {
        userRepository.findById(idDelUsuario).ifPresent(usuario -> {
            usuario.setEnabled(false);
            userRepository.save(usuario);
        });
    }

    private static void consultar(String desde, String hasta) {
        theActorCalled(ADMINISTRADOR).attemptsTo(ConsultarResumenDeLaPlataforma.delPeriodo(desde, hasta));
    }
}
