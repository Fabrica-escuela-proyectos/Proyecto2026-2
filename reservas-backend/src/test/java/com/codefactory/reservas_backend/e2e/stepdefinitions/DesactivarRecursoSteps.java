package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeRecursos;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReservas;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarDisponibilidad;
import com.codefactory.reservas_backend.e2e.tasks.DesactivarRecurso;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.domain.CancelOrigin;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.RecursosComunesSteps.enLaBase;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.RecursosComunesSteps.idDelRecurso;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.RecursosComunesSteps.prepararProveedorConRecurso;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.RecursosComunesSteps.prepararRecursoDeOtroNegocio;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.agenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarReservas;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.reservarPorLaApi;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.reservas;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * HU-16 - Desactivar recurso. Actores: el proveedor dueño, otro proveedor (dueño del recurso ajeno) y un cliente que
 * hizo las reservas futuras. "an authenticated Provider", "the operation is not allowed" y "the resource keeps its
 * current status" están en RecursosComunesSteps. Las reservas futuras (a 7 y 8 días) se crean por la API y su estado se
 * comprueba en la base.
 * Confirmar es enviar la solicitud con "confirm": true; "no confirmar" es enviarla con "confirm": false: la API sigue
 * pidiendo la confirmación y no cambia nada.
 */
public class DesactivarRecursoSteps {

    private static final String CONFIRMACION_REQUERIDA = "CONFIRMATION_REQUIRED";

    @Autowired
    private ResourceRepository recursos;
    @Autowired
    private BookingRepository reservasEnLaBase;

    // ---------- Given ----------

    @Given("the resource has no future bookings")
    public void elRecursoNoTieneReservasFuturas() {
        UUID recurso = UUID.fromString(idDelRecurso());
        assertThat(reservasEnLaBase.findAll().stream().filter(r -> recurso.equals(r.getResourceId()))).isEmpty();
    }

    @Given("the resource has future bookings")
    public void elRecursoTieneReservasFuturas() {
        crearReservasFuturas();
    }

    /** Proveedor con su recurso, dos reservas futuras de un cliente, y la primera solicitud (sin confirmar). */
    @Given("a Provider has requested to deactivate a resource with future bookings")
    public void proveedorSolicitoDesactivarConReservasFuturas() {
        prepararProveedorConRecurso();
        crearReservasFuturas();
        solicitarDesactivacion(null);
    }

    @And("the Provider was informed of the number of affected bookings")
    public void elProveedorFueInformadoDeLaCantidad() {
        verificarQueSeInformoDeLaCantidad();
    }

    @Given("a resource that belongs to another business")
    public void unRecursoDeOtroNegocio() {
        prepararRecursoDeOtroNegocio(false);
    }

    // ---------- When ----------

    @When("they request to deactivate the resource")
    public void solicitaDesactivarElRecurso() {
        solicitarDesactivacion(null);
    }

    @When("the Provider confirms the deactivation")
    public void confirmaLaDesactivacion() {
        solicitarDesactivacion(true);
    }

    @When("the Provider does not confirm the deactivation")
    public void noConfirmaLaDesactivacion() {
        solicitarDesactivacion(false);
    }

    // ---------- Then / And ----------

    @Then("the resource becomes deactivated")
    public void elRecursoQuedaDesactivado() {
        String id = idDelRecurso();
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeRecursos.activo(), is(false)));
        assertThat(enLaBase(recursos, id).isActive()).isFalse();
    }

    /** Sin recursos activos, el servicio no tiene horarios libres en ninguna fecha. */
    @And("the resource is no longer available for new bookings")
    public void yaNoEstaDisponibleParaNuevasReservas() {
        Agenda agenda = agenda();
        Actor cliente = theActorCalled(CLIENTE);
        cliente.attemptsTo(ConsultarDisponibilidad.delServicio(agenda.idDelServicio(),
                AgendaDePrueba.hoy().plusDays(7).toString()));
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        assertThat(RespuestaDeReservas.horariosLibres().answeredBy(cliente)).isEmpty();
    }

    @Then("the Provider is informed of the number of future bookings that will be affected")
    public void seInformaLaCantidadDeReservasAfectadas() {
        verificarQueSeInformoDeLaCantidad();
    }

    /** 409 CONFIRMATION_REQUIRED y nada cambió: el recurso sigue activo y las reservas, confirmadas. */
    @And("they must confirm before continuing")
    public void debeConfirmarAntesDeContinuar() {
        String id = idDelRecurso();
        List<String> ids = reservas();
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeLaApi.codigoDeError(), equalTo(CONFIRMACION_REQUERIDA)));
        assertThat(enLaBase(recursos, id).isActive()).isTrue();
        verificarReservasConfirmadas(ids);
    }

    @And("the affected future bookings are cancelled")
    public void lasReservasFuturasQuedanCanceladas() {
        List<String> ids = reservas();
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeRecursos.reservasCanceladas(), equalTo(ids.size())));
        for (String id : ids) {
            Booking reserva = reserva(id);
            assertThat(reserva.getStatus()).as("estado de la reserva %s", id).isEqualTo(BookingStatus.CANCELADA);
            assertThat(reserva.getCancelOrigin()).as("origen de la reserva %s", id)
                    .isEqualTo(CancelOrigin.RECURSO_NO_DISPONIBLE);
        }
    }

    @Then("the resource remains active")
    public void elRecursoPermaneceActivo() {
        assertThat(enLaBase(recursos, idDelRecurso()).isActive()).isTrue();
    }

    @And("the future bookings of the resource are not cancelled")
    public void lasReservasFuturasNoSeCancelan() {
        verificarReservasConfirmadas(reservas());
    }

    // ---------- Apoyo ----------

    /** Un cliente con sesión reserva dos veces el servicio del recurso, a 7 y 8 días de hoy. */
    private void crearReservasFuturas() {
        Agenda agenda = agenda();
        Actor cliente = theActorCalled(CLIENTE);
        CuentasDePrueba.registrarEIniciarSesion(cliente, "Cliente");
        String primera = reservarPorLaApi(cliente, agenda, 7);
        String segunda = reservarPorLaApi(cliente, agenda, 8);
        guardarReservas(List.of(primera, segunda));
    }

    /** El proveedor pide desactivar "el recurso"; el actor en foco queda siendo él. */
    private void solicitarDesactivacion(Boolean confirmar) {
        String id = idDelRecurso();
        theActorCalled(PROVEEDOR).attemptsTo(DesactivarRecurso.conId(id, confirmar));
    }

    private void verificarQueSeInformoDeLaCantidad() {
        String cantidad = String.valueOf(reservas().size());
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(409)),
                seeThat(RespuestaDeRecursos.reservasAfectadas(), equalTo(cantidad)));
    }

    private void verificarReservasConfirmadas(List<String> ids) {
        for (String id : ids) {
            Booking reserva = reserva(id);
            assertThat(reserva.getStatus()).as("estado de la reserva %s", id).isEqualTo(BookingStatus.CONFIRMADA);
            assertThat(reserva.getCancelledAt()).as("cancelación de la reserva %s", id).isNull();
        }
    }

    private Booking reserva(String id) {
        return reservasEnLaBase.findById(UUID.fromString(id)).orElseThrow(
                () -> new AssertionError("La reserva " + id + " ya no existe en la base"));
    }
}
