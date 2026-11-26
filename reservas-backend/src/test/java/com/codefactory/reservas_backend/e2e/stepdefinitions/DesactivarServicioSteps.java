package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.ElNegocio;
import com.codefactory.reservas_backend.e2e.questions.ElServicio;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.DesactivarServicio;
import com.codefactory.reservas_backend.e2e.tasks.ReservarServicio;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.MemoriaDeServicios;
import com.codefactory.reservas_backend.e2e.user.ServiciosDePrueba;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.PROVEEDOR_EXISTENTE;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * HU-11 - Desactivar servicio. Las reservas futuras las crea un cliente por la API (AgendaDePrueba del equipo);
 * la reserva pasada se guarda directo en la base, porque la API no deja reservar en el pasado.
 */
public class DesactivarServicioSteps {

    @Autowired
    private BookingRepository bookingRepository;

    // ---------- Given ----------

    @Given("an active service has no future bookings")
    public void servicioActivoSinReservas() {
        prepararServicioConAgenda();
    }

    @Given("an active service has {int} future bookings")
    public void servicioConReservasFuturas(int cantidad) {
        Agenda agenda = prepararServicioConAgenda();
        Actor cliente = ServiciosDePrueba.cliente();
        List<String> reservas = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            reservas.add(AgendaDePrueba.reservar(cliente, agenda, AgendaDePrueba.horarioEn(2 + i)));
        }
        ServiciosDePrueba.dueno().remember(MemoriaDeServicios.RESERVAS, reservas);
    }

    @And("the provider asked to deactivate it and was told how many bookings would be affected")
    public void yaPidioDesactivarYFueAvisado() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.attemptsTo(DesactivarServicio.sinConfirmar(ServiciosDePrueba.servicioDelDueno()));
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeError(), equalTo("CONFIRMATION_REQUIRED")));
    }

    @Given("an active service has a completed booking in the past and a future booking")
    public void servicioConReservaPasadaYFutura() {
        Agenda agenda = prepararServicioConAgenda();
        Actor cliente = ServiciosDePrueba.cliente();
        String pasada = AgendaDePrueba.insertarReserva(bookingRepository, agenda, cliente,
                AgendaDePrueba.a(AgendaDePrueba.hoy().minusDays(10), 10), BookingStatus.COMPLETADA);
        String futura = AgendaDePrueba.reservar(cliente, agenda, AgendaDePrueba.horarioEn(3));
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.remember(MemoriaDeServicios.RESERVA_PASADA, pasada);
        dueno.remember(MemoriaDeServicios.RESERVAS, List.of(pasada, futura));
    }

    @Given("a service was deactivated successfully")
    public void servicioDesactivado() {
        prepararServicioConAgenda();
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.attemptsTo(DesactivarServicio.sinConfirmar(ServiciosDePrueba.servicioDelDueno()));
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    // ---------- When ----------

    @When("the provider asks to deactivate the service")
    public void pideDesactivar() {
        ServiciosDePrueba.dueno().attemptsTo(DesactivarServicio.sinConfirmar(ServiciosDePrueba.servicioDelDueno()));
    }

    @When("the provider asks to deactivate the service and confirms the operation")
    public void pideDesactivarYConfirma() {
        ServiciosDePrueba.dueno().attemptsTo(DesactivarServicio.confirmando(ServiciosDePrueba.servicioDelDueno()));
    }

    /** No confirmar es no enviar la segunda petición (con "confirm": true). Aquí no se envía nada. */
    @When("they do not confirm the operation")
    public void noConfirma() {
        // El proveedor no hace nada más: la desactivación se queda sin confirmar.
    }

    @When("a client tries to create a new booking for that service")
    public void elClienteIntentaReservarElServicioDesactivado() {
        String servicio = ServiciosDePrueba.servicioDelDueno();
        ServiciosDePrueba.cliente().attemptsTo(
                ReservarServicio.en(servicio, AgendaDePrueba.horarioEn(2), AgendaDePrueba.DURACION));
    }

    @When("they try to deactivate that service")
    public void otroProveedorIntentaDesactivar() {
        String servicioAjeno = ServiciosDePrueba.servicioDelDueno();
        theActorCalled(OTRO_PROVEEDOR).attemptsTo(DesactivarServicio.sinConfirmar(servicioAjeno));
    }

    /** Lo usan el cliente y el usuario sin sesión: actúa quien esté en el foco después del Given. */
    @When("they try to deactivate a service")
    public void intentaDesactivarUnServicio() {
        Actor quienIntenta = theActorInTheSpotlight();
        String servicio = ServiciosDePrueba.crearServicioValido();
        ServiciosDePrueba.enElFoco(quienIntenta).attemptsTo(DesactivarServicio.sinConfirmar(servicio));
    }

    // ---------- Then / And ----------

    @Then("the service is deactivated")
    public void elServicioQuedaDesactivado() {
        ServiciosDePrueba.dueno().should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(ElServicio.estaActivo(), is(false)));
    }

    @And("no new bookings are allowed for that service")
    public void noSePermitenNuevasReservas() {
        elClienteIntentaReservarElServicioDesactivado();
        ServiciosDePrueba.cliente().should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(404)),
                seeThat(RespuestaDeLaApi.mensaje(), equalTo("El servicio no está disponible")));
    }

    @Then("the provider is told that {int} future bookings would be affected and must confirm before continuing")
    public void seLePideConfirmar(int cantidad) {
        ServiciosDePrueba.dueno().should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(409)),
                seeThat(RespuestaDeLaApi.codigoDeError(), equalTo("CONFIRMATION_REQUIRED")),
                seeThat(RespuestaDeLaApi.mensajeDelCampo("affectedBookings"), equalTo(String.valueOf(cantidad))));
    }

    @Then("the service stays active")
    public void elServicioSigueActivo() {
        Map<String, Object> guardado = ServiciosDePrueba.servicioGuardado(ServiciosDePrueba.servicioDelDueno());
        assertThat(guardado.get("active")).as("el servicio sigue activo").isEqualTo(true);
    }

    @And("the {int} future bookings are in status {string}")
    public void lasReservasFuturasQuedanEnEstado(int cantidad, String estado) {
        List<Booking> reservas = reservasDelEscenario();
        assertThat(reservas).hasSize(cantidad);
        reservas.forEach(reserva -> assertThat(reserva.getStatus()).isEqualTo(estadoEnLaBase(estado)));
    }

    @And("the reason of the cancellation is {string}")
    public void elMotivoEs(String motivo) {
        reservasDelEscenario().forEach(reserva -> {
            assertThat(reserva.getCancelOrigin()).isEqualTo(CancelOrigin.SERVICIO_NO_DISPONIBLE);
            assertThat(reserva.getCancelReason()).containsIgnoringCase(motivo);
        });
    }

    @And("the future bookings are not cancelled")
    public void lasReservasNoSeCancelan() {
        reservasDelEscenario().forEach(reserva -> assertThat(reserva.getStatus()).isEqualTo(BookingStatus.CONFIRMADA));
    }

    @Then("the past booking keeps its status {string}")
    public void laReservaPasadaConservaSuEstado(String estado) {
        String pasada = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.RESERVA_PASADA);
        assertThat(bookingRepository.findById(UUID.fromString(pasada)).orElseThrow().getStatus())
                .isEqualTo(estadoEnLaBase(estado));
    }

    @And("no booking is deleted")
    public void ningunaReservaSeElimina() {
        List<String> ids = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.RESERVAS);
        ids.forEach(id -> assertThat(bookingRepository.findById(UUID.fromString(id))).as("reserva " + id).isPresent());
    }

    @Then("the service does not appear among the available services")
    public void noApareceEnElCatalogo() {
        String servicio = ServiciosDePrueba.servicioDelDueno();
        List<String> visibles = ElNegocio.idsDeLosServicios().answeredBy(ServiciosDePrueba.cliente());
        assertThat(visibles).as("servicios que ve el cliente").doesNotContain(servicio);
    }

    @And("they receive a message saying the service is not available")
    public void mensajeDeServicioNoDisponible() {
        ServiciosDePrueba.cliente().should(seeThat(RespuestaDeLaApi.mensaje(), equalTo("El servicio no está disponible")));
    }

    @And("the status of the service does not change")
    public void elEstadoNoCambia() {
        elServicioSigueActivo();
    }

    // ---------- Apoyo ----------

    /** Negocio listo para recibir reservas (servicio con recurso y horario), del proveedor dueño. */
    private Agenda prepararServicioConAgenda() {
        Actor dueno = theActorCalled(PROVEEDOR_EXISTENTE);
        Agenda agenda = AgendaDePrueba.prepararNegocio(dueno);
        dueno.remember(MemoriaDeServicios.AGENDA, agenda);
        dueno.remember(MemoriaDeServicios.ID_DE_SERVICIO, agenda.idDelServicio());
        dueno.remember(MemoriaDeServicios.DURACION, agenda.duracionEnMinutos());
        return agenda;
    }

    private List<Booking> reservasDelEscenario() {
        List<String> ids = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.RESERVAS);
        return ids.stream().map(id -> bookingRepository.findById(UUID.fromString(id)).orElseThrow()).toList();
    }

    /** "Cancelada" o "Completada" en el feature son CANCELADA o COMPLETADA en la base. */
    static BookingStatus estadoEnLaBase(String estado) {
        return BookingStatus.valueOf(estado.trim().toUpperCase());
    }
}
