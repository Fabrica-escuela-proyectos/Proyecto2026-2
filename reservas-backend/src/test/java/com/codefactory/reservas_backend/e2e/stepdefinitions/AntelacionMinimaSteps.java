package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.models.Horario;
import com.codefactory.reservas_backend.e2e.questions.LaAntelacion;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.DefinirAntelacion;
import com.codefactory.reservas_backend.e2e.tasks.ReservarServicio;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.e2e.user.MemoriaDeServicios;
import com.codefactory.reservas_backend.e2e.user.ServiciosDePrueba;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.PROVEEDOR_EXISTENTE;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * HU-08 - Definir la antelación mínima de reserva. Por defecto cada negocio exige 1 hora.
 * Para probar que una reserva se rechaza por antelación basta un servicio corto (5 minutos): la API revisa la
 * antelación antes que los recursos y el horario de atención, así que no hace falta asignarle un recurso.
 */
public class AntelacionMinimaSteps {

    private static final int ANTELACION_POR_DEFECTO = 1;
    private static final int DURACION_CORTA = 5;

    @Autowired
    private BookingRepository bookingRepository;

    // ---------- Given ----------

    @Given("a provider registered a business and has not defined the advance notice")
    public void negocioSinAntelacionDefinida() {
        crearServicioCorto();
    }

    @Given("a business has a minimum advance notice of {int} hours")
    public void negocioConAntelacion(int horas) {
        crearServicioCorto();
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.attemptsTo(DefinirAntelacion.de(ServiciosDePrueba.negocioDelDueno(), horas));
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    /** Dos reservas confirmadas hechas por la API; se guarda cuándo empieza cada una para compararlas después. */
    @Given("a business has confirmed bookings")
    public void negocioConReservasConfirmadas() {
        Actor dueno = theActorCalled(PROVEEDOR_EXISTENTE);
        Agenda agenda = AgendaDePrueba.prepararNegocio(dueno);
        Actor cliente = ServiciosDePrueba.cliente();
        Map<String, Instant> antes = new LinkedHashMap<>();
        for (int dias = 2; dias <= 3; dias++) {
            String reserva = AgendaDePrueba.reservar(cliente, agenda, AgendaDePrueba.horarioEn(dias));
            antes.put(reserva, bookingRepository.findById(UUID.fromString(reserva)).orElseThrow().getStartAt());
        }
        ServiciosDePrueba.dueno().remember(MemoriaDeServicios.RESERVAS_ANTES, antes);
    }

    // ---------- When ----------

    @When("a client tries to book a service of that business {int} minutes in advance")
    public void elClienteReservaConMinutos(int minutos) {
        reservarDentroDe(minutos);
    }

    @When("a client tries to book {int} hour(s) in advance")
    public void elClienteReservaConHoras(int horas) {
        reservarDentroDe(horas * 60);
    }

    @When("they define a minimum advance notice of {int} hours")
    public void defineLaAntelacion(int horas) {
        definir(horas);
    }

    @When("they enter the advance notice {string}")
    public void ingresaLaAntelacion(String valor) {
        definir(ServiciosDePrueba.valor(valor));
    }

    /** Pasa a 72 horas (3 días): más que la anticipación con la que se hicieron las reservas del Given. */
    @When("the provider changes the minimum advance notice")
    public void cambiaLaAntelacion() {
        definir(72);
    }

    @When("they define the minimum advance notice of one of them")
    public void defineLaAntelacionDeUnNegocio() {
        definir(5);
    }

    @When("they try to define the advance notice of a business that belongs to another provider")
    public void otroProveedorIntentaDefinir() {
        String negocioAjeno = ServiciosDePrueba.negocioDelDueno();
        theActorCalled(OTRO_PROVEEDOR).attemptsTo(DefinirAntelacion.de(negocioAjeno, 5));
    }

    /** Lo usan el cliente y el usuario sin sesión: actúa quien esté en el foco después del Given. */
    @When("they try to define the minimum advance notice of a business")
    public void intentaDefinirLaAntelacion() {
        Actor quienIntenta = theActorInTheSpotlight();
        String negocio = ServiciosDePrueba.negocioDelDueno();
        ServiciosDePrueba.enElFoco(quienIntenta).attemptsTo(DefinirAntelacion.de(negocio, 5));
    }

    // ---------- Then / And ----------

    @And("they receive a message saying the business requires at least {int} hour(s) of advance notice")
    public void mensajeDeAntelacion(int horas) {
        String esperado = "al menos " + horas + (horas == 1 ? " hora" : " horas") + " de antelación";
        ServiciosDePrueba.cliente().should(seeThat(RespuestaDeLaApi.mensaje(), containsString(esperado)));
    }

    /** La respuesta trae la antelación nueva, y al consultarla de nuevo sigue guardada. */
    @Then("the new advance notice is saved")
    public void laAntelacionQuedaGuardada() {
        Actor dueno = ServiciosDePrueba.dueno();
        Integer esperada = dueno.recall(MemoriaDeServicios.ANTELACION_ESPERADA);
        dueno.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(LaAntelacion.enHoras(), equalTo(esperada)));
        assertThat(ServiciosDePrueba.antelacionGuardada(ServiciosDePrueba.negocioDelDueno())).isEqualTo(esperada);
    }

    @And("they receive the confirmation that the change was successful")
    public void confirmacionDelCambio() {
        Actor dueno = ServiciosDePrueba.dueno();
        String negocio = dueno.recall(Memoria.ID_DE_NEGOCIO);
        dueno.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeLaApi.idDelNegocio(), equalTo(negocio)));
    }

    @Then("the confirmed bookings stay the same")
    public void lasReservasNoCambian() {
        ServiciosDePrueba.dueno().should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        Map<String, Instant> antes = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.RESERVAS_ANTES);
        antes.forEach((id, inicio) -> {
            Booking reserva = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
            assertThat(reserva.getStatus()).as("estado de la reserva").isEqualTo(BookingStatus.CONFIRMADA);
            assertThat(reserva.getStartAt()).as("inicio de la reserva").isEqualTo(inicio);
        });
    }

    @Then("the other businesses keep their advance notice")
    public void losOtrosNegociosConservanSuAntelacion() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        String otroNegocio = dueno.recall(MemoriaDeServicios.OTRO_NEGOCIO);
        assertThat(ServiciosDePrueba.antelacionGuardada(otroNegocio)).isEqualTo(ANTELACION_POR_DEFECTO);
    }

    @Then("the change is rejected and the business keeps its previous advance notice")
    public void elCambioEsRechazado() {
        ServiciosDePrueba.dueno().should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)));
        assertThat(ServiciosDePrueba.antelacionGuardada(ServiciosDePrueba.negocioDelDueno()))
                .isEqualTo(ANTELACION_POR_DEFECTO);
    }

    @And("they receive a message saying the advance notice is not valid")
    public void mensajeDeAntelacionInvalida() {
        ServiciosDePrueba.dueno().should(seeThat(RespuestaDeLaApi.mensajeDelCampo("hours"), not(emptyOrNullString())));
    }

    @And("the advance notice of that business does not change")
    public void laAntelacionNoCambia() {
        assertThat(ServiciosDePrueba.antelacionGuardada(ServiciosDePrueba.negocioDelDueno()))
                .isEqualTo(ANTELACION_POR_DEFECTO);
    }

    // ---------- Apoyo ----------

    private void crearServicioCorto() {
        Map<String, Object> datos = ServiciosDePrueba.datosValidos();
        datos.put("durationMinutes", DURACION_CORTA);
        ServiciosDePrueba.crearServicio(datos);
    }

    /** Guarda la antelación esperada (si es un número) y el dueño la envía. */
    private void definir(Object horas) {
        Actor dueno = ServiciosDePrueba.dueno();
        if (horas instanceof Number numero) {
            dueno.remember(MemoriaDeServicios.ANTELACION_ESPERADA, numero.intValue());
        }
        dueno.attemptsTo(DefinirAntelacion.de(ServiciosDePrueba.negocioDelDueno(), horas));
    }

    /** El cliente intenta reservar el servicio corto para dentro de esos minutos (hora de Bogotá). */
    private void reservarDentroDe(int minutos) {
        String servicio = ServiciosDePrueba.servicioDelDueno();
        LocalDateTime inicio = LocalDateTime.now(AgendaDePrueba.BOGOTA).plusMinutes(minutos).truncatedTo(ChronoUnit.MINUTES);
        Horario horario = new Horario(inicio.toLocalDate(), inicio.toLocalTime());
        ServiciosDePrueba.cliente().attemptsTo(ReservarServicio.en(servicio, horario, DURACION_CORTA));
    }
}
