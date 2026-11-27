package com.codefactory.reservas_backend.e2e.stepdefinitions.us23;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeHorariosYRecursos;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReservas;
import com.codefactory.reservas_backend.e2e.tasks.CancelarReserva;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarReservas;
import com.codefactory.reservas_backend.e2e.tasks.ReservarServicio;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.codefactory.reservas_backend.e2e.user.MemoriaDeHorarios.RESERVA_CANCELADA;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeHorarios.RESERVA_CONFIRMADA;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.MOTIVO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CONTEXTO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.OTRO_CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * US 23: consultar mis reservas.
 * Reutiliza el paso "access is denied" de ComunesSteps dejando al cliente en foco tras la petición.
 */
public class ConsultarMisReservasSteps {

    private static final String MOTIVO_CANCELACION = "Mantenimiento programado del recurso";

    @Given("a Client has a confirmed booking and a cancelled booking")
    public void clienteConReservaConfirmadaYCancelada() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        Agenda agenda = AgendaDePrueba.prepararNegocio(proveedor);
        Actor cliente = theActorCalled(CLIENTE);
        CuentasDePrueba.registrarEIniciarSesion(cliente, "Cliente");

        LocalDate primeraFecha = AgendaDePrueba.hoy().plusDays(7);
        cliente.attemptsTo(ReservarServicio.entre(agenda.idDelServicio(), primeraFecha, "09:00", "10:00"));
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        String idConfirmada = RespuestaDeHorariosYRecursos.idDeLaReserva().answeredBy(cliente);

        LocalDate segundaFecha = AgendaDePrueba.hoy().plusDays(8);
        cliente.attemptsTo(ReservarServicio.entre(agenda.idDelServicio(), segundaFecha, "10:00", "11:00"));
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        String idCancelada = RespuestaDeHorariosYRecursos.idDeLaReserva().answeredBy(cliente);

        proveedor.attemptsTo(CancelarReserva.comoProveedor(idCancelada, MOTIVO_CANCELACION));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));

        Actor contexto = theActorCalled(CONTEXTO);
        contexto.remember(RESERVA_CONFIRMADA, idConfirmada);
        contexto.remember(RESERVA_CANCELADA, idCancelada);
        contexto.remember(MOTIVO, MOTIVO_CANCELACION);
    }

    @Given("a Client has not made any booking")
    @Given("a Client is logged in")
    public void clienteAutenticadoSinReservas() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(CLIENTE), "Cliente");
    }

    @When("they check their bookings")
    public void consultaSusReservas() {
        theActorCalled(CLIENTE).attemptsTo(ConsultarReservas.propias());
    }

    @When("they try to see the bookings of another client")
    public void intentaVerReservasDeOtroCliente() {
        Actor otroCliente = theActorCalled(OTRO_CLIENTE);
        CuentasDePrueba.registrar(otroCliente, "Cliente");
        String idDeOtroCliente = otroCliente.recall(Memoria.ID_DE_USUARIO);

        // El cliente queda al final (en el foco) para que "access is denied" de ComunesSteps verifique su respuesta.
        theActorCalled(CLIENTE).attemptsTo(ConsultarReservas.delUsuario(idDeOtroCliente));
    }

    @Then("they see both bookings with their date, time, service and status")
    public void veAmbasReservasConSusDatos() {
        Actor contexto = theActorCalled(CONTEXTO);
        String idConfirmada = contexto.recall(RESERVA_CONFIRMADA);
        String idCancelada = contexto.recall(RESERVA_CANCELADA);

        Actor cliente = theActorCalled(CLIENTE);
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        List<Map<String, Object>> reservas = RespuestaDeReservas.reservasDeLaLista().answeredBy(cliente);

        Map<String, Object> confirmada = buscarPorId(reservas, idConfirmada);
        Map<String, Object> cancelada = buscarPorId(reservas, idCancelada);

        verificarCamposBasicos(confirmada, BookingStatus.CONFIRMADA.name());
        verificarCamposBasicos(cancelada, BookingStatus.CANCELADA.name());
    }

    @And("the cancelled booking shows its cancellation reason")
    public void laReservaCanceladaMuestraSuMotivo() {
        Actor contexto = theActorCalled(CONTEXTO);
        String idCancelada = contexto.recall(RESERVA_CANCELADA);
        String motivoEsperado = contexto.recall(MOTIVO);

        List<Map<String, Object>> reservas = RespuestaDeReservas.reservasDeLaLista().answeredBy(theActorCalled(CLIENTE));
        Map<String, Object> cancelada = buscarPorId(reservas, idCancelada);
        assertThat(cancelada.get("cancelReason")).isEqualTo(motivoEsperado);
    }

    @Then("they are informed that they have no bookings")
    public void seInformaQueNoTieneReservas() {
        Actor cliente = theActorCalled(CLIENTE);
        cliente.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeLaApi.mensaje(), equalTo("No tiene reservas registradas")));
        assertThat(RespuestaDeReservas.reservasDeLaLista().answeredBy(cliente)).isEmpty();
    }

    private static Map<String, Object> buscarPorId(List<Map<String, Object>> reservas, String id) {
        return reservas.stream()
                .filter(item -> id.equals(item.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró la reserva " + id + " en la respuesta"));
    }

    private static void verificarCamposBasicos(Map<String, Object> reserva, String estadoEsperado) {
        assertThat(reserva.get("date")).isNotNull();
        assertThat(reserva.get("startTime")).isNotNull();
        assertThat(reserva.get("endTime")).isNotNull();
        assertThat(reserva.get("serviceName")).isNotNull();
        assertThat(reserva.get("status")).isEqualTo(estadoEsperado);
    }
}
