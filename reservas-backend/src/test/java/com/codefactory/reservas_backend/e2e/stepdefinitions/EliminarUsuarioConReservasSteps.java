package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.models.EstadosDeReserva;
import com.codefactory.reservas_backend.e2e.models.Horario;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.AutenticarseComoAdministrador;
import com.codefactory.reservas_backend.e2e.tasks.CancelarReserva;
import com.codefactory.reservas_backend.e2e.tasks.EliminarUsuario;
import com.codefactory.reservas_backend.e2e.tasks.ReservarServicio;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import com.codefactory.reservas_backend.reservation.domain.Booking;
import com.codefactory.reservas_backend.reservation.domain.BookingStatus;
import com.codefactory.reservas_backend.reservation.infrastructure.BookingRepository;
import com.codefactory.reservas_backend.support.TotpTestSupport;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.agenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarAgenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarReservas;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.prepararAgendaYCliente;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.primeraReserva;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.reservarPorLaApi;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.reservas;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.CUENTA_A_ELIMINAR;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.RESERVAS_ANTES;
import static com.codefactory.reservas_backend.e2e.user.Personajes.ADMINISTRADOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * HU-28 - Cancelar reservas futuras al eliminar un usuario. El administrador (con verificación adicional)
 * elimina la cuenta guardada en CUENTA_A_ELIMINAR: la del cliente o la del proveedor del escenario.
 */
public class EliminarUsuarioConReservasSteps {

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- Given: cuentas de cliente ----------

    @Given("a client has confirmed bookings on future dates")
    public void clienteConReservasFuturas() {
        Agenda agenda = prepararAgendaYCliente();
        Actor cliente = theActorCalled(CLIENTE);
        guardarReservas(List.of(reservarPorLaApi(cliente, agenda, 7), reservarPorLaApi(cliente, agenda, 9)));
        eliminaraA(CLIENTE);
    }

    @Given("a client has past bookings")
    public void clienteConReservasPasadas() {
        Agenda agenda = prepararAgendaYCliente();
        guardarReservasPasadas(agenda);
        eliminaraA(CLIENTE);
    }

    @Given("a client has a future booking cancelled with origin {string}")
    public void clienteConReservaFuturaCancelada(String origen) {
        if (!"Client".equals(origen)) {
            throw new IllegalArgumentException("Solo el cliente cancela por la API su propia reserva: " + origen);
        }
        Agenda agenda = prepararAgendaYCliente();
        Actor cliente = theActorCalled(CLIENTE);
        String id = reservarPorLaApi(cliente, agenda, 7);
        cliente.attemptsTo(CancelarReserva.comoCliente(id));
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        guardarReservas(List.of(id));
        eliminaraA(CLIENTE);
    }

    @Given("the administrator deleted a client with a future booking")
    public void administradorEliminoAUnClienteConReserva() {
        Agenda agenda = prepararAgendaYCliente();
        guardarReservas(List.of(reservarPorLaApi(theActorCalled(CLIENTE), agenda, 7)));
        eliminaraA(CLIENTE);
        eliminarLaCuenta();
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(204)));
    }

    @Given("a client has no future bookings")
    public void clienteSinReservasFuturas() {
        CuentasDePrueba.registrar(theActorCalled(CLIENTE), "Cliente");
        eliminaraA(CLIENTE);
    }

    // ---------- Given: cuentas de proveedor ----------

    @Given("a provider has confirmed bookings on future dates in their business")
    public void proveedorConReservasFuturas() {
        Agenda agenda = prepararAgendaYCliente();
        Actor cliente = theActorCalled(CLIENTE);
        guardarReservas(List.of(reservarPorLaApi(cliente, agenda, 7), reservarPorLaApi(cliente, agenda, 9)));
        eliminaraA(PROVEEDOR);
    }

    @Given("the administrator deleted the account of a provider")
    public void administradorEliminoAUnProveedor() {
        guardarAgenda(AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR)));
        eliminaraA(PROVEEDOR);
        eliminarLaCuenta();
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(204)));
    }

    @Given("a provider has past bookings in their business")
    public void proveedorConReservasPasadas() {
        Agenda agenda = prepararAgendaYCliente();
        guardarReservasPasadas(agenda);
        eliminaraA(PROVEEDOR);
    }

    // ---------- When ----------

    @When("the administrator deletes their account")
    public void elAdministradorEliminaLaCuenta() {
        eliminarLaCuenta();
    }

    @When("a client tries to book a service of that business")
    public void unClienteIntentaReservar() {
        Agenda agenda = agenda();
        Horario horario = AgendaDePrueba.horarioEn(7);
        Actor cliente = theActorCalled(CLIENTE);
        CuentasDePrueba.registrarEIniciarSesion(cliente, "Cliente");
        cliente.attemptsTo(ReservarServicio.en(agenda.idDelServicio(), horario, agenda.duracionEnMinutos()));
    }

    /** Quien intenta es el "Usuario" (Cliente o Proveedor, sin rol de administrador); la cuenta es la del cliente. */
    @When("they try to delete the account of another user")
    public void intentaEliminarOtraCuenta() {
        clienteConReservasFuturas();
        String cuenta = contexto().recall(CUENTA_A_ELIMINAR);
        theActorCalled(USUARIO).attemptsTo(EliminarUsuario.con(cuenta, null));
    }

    @When("they try to delete an account")
    public void intentaEliminarUnaCuentaSinSesion() {
        clienteSinReservasFuturas();
        String cuenta = contexto().recall(CUENTA_A_ELIMINAR);
        theActorCalled(USUARIO).attemptsTo(EliminarUsuario.con(cuenta, null));
    }

    // ---------- Then / And ----------

    @Then("their future bookings are in status {string}")
    @Then("the future bookings of their business are in status {string}")
    public void lasReservasFuturasQuedanEnEstado(String estado) {
        laEliminacionFueExitosa();
        for (String id : reservas()) {
            assertThat(reserva(id).getStatus()).as("estado de la reserva %s", id)
                    .isEqualTo(EstadosDeReserva.estado(estado));
        }
    }

    @Then("their past bookings are kept unchanged in the history")
    @Then("the past bookings of their business are kept unchanged in the history")
    public void lasReservasPasadasSeConservan() {
        laEliminacionFueExitosa();
        Map<String, Booking> antes = contexto().recall(RESERVAS_ANTES);
        for (String id : reservas()) {
            Booking ahora = reserva(id);
            Booking previa = antes.get(id);
            assertThat(ahora.getStatus()).as("estado de la reserva %s", id).isEqualTo(previa.getStatus());
            assertThat(ahora.getStartAt()).as("inicio de la reserva %s", id).isEqualTo(previa.getStartAt());
            assertThat(ahora.getPriceCop()).as("precio de la reserva %s", id).isEqualTo(previa.getPriceCop());
            assertThat(ahora.getServiceName()).as("servicio de la reserva %s", id).isEqualTo(previa.getServiceName());
        }
    }

    @Then("the cancelled bookings still exist in the history")
    public void lasCanceladasSiguenEnElHistorial() {
        laEliminacionFueExitosa();
        for (String id : reservas()) {
            assertThat(reserva(id).getStatus()).as("estado de la reserva %s", id).isEqualTo(BookingStatus.CANCELADA);
        }
    }

    @Then("the booking keeps the origin {string}")
    public void conservaElOrigen(String origen) {
        laEliminacionFueExitosa();
        Booking reserva = reserva(primeraReserva());
        assertThat(reserva.getStatus()).isEqualTo(BookingStatus.CANCELADA);
        assertThat(reserva.getCancelOrigin()).isEqualTo(EstadosDeReserva.origen(origen));
    }

    @Then("the account is deleted successfully")
    public void laCuentaQuedaEliminada() {
        laEliminacionFueExitosa();
        String cuenta = contexto().recall(CUENTA_A_ELIMINAR);
        assertThat(userRepository.findById(UUID.fromString(cuenta))).isEmpty();
    }

    /** El servicio se borró con el proveedor: la API responde 404 "El servicio no está disponible". */
    @Then("the booking is not allowed")
    public void laReservaNoEsPermitida() {
        theActorCalled(CLIENTE).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(404)));
    }

    @And("the bookings of that user do not change")
    public void lasReservasDelUsuarioNoCambian() {
        String cuenta = contexto().recall(CUENTA_A_ELIMINAR);
        assertThat(userRepository.findById(UUID.fromString(cuenta))).isPresent();
        for (String id : reservas()) {
            assertThat(reserva(id).getStatus()).as("estado de la reserva %s", id).isEqualTo(BookingStatus.CONFIRMADA);
        }
    }

    // ---------- Apoyo ----------

    private void eliminaraA(String actor) {
        String id = theActorCalled(actor).recall(Memoria.ID_DE_USUARIO);
        contexto().remember(CUENTA_A_ELIMINAR, id);
    }

    /** Crea un administrador con verificación adicional y elimina la cuenta del escenario con su código vigente. */
    private void eliminarLaCuenta() {
        String cuenta = contexto().recall(CUENTA_A_ELIMINAR);
        User datosDelAdministrador = CuentasDePrueba.crearAdministrador(userRepository, roleRepository, passwordEncoder);
        Actor administrador = theActorCalled(ADMINISTRADOR);
        administrador.attemptsTo(
                AutenticarseComoAdministrador.conVerificacion(CuentasDePrueba.credencialesDe(datosDelAdministrador)));
        String secreto = administrador.recall(Memoria.SECRETO_MFA);
        administrador.attemptsTo(EliminarUsuario.con(cuenta, TotpTestSupport.codeNow(secreto)));
    }

    private void laEliminacionFueExitosa() {
        theActorCalled(ADMINISTRADOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(204)));
    }

    /** Una reserva completada y una confirmada que ya pasó (nunca se completó); se guarda una copia de cada una. */
    private void guardarReservasPasadas(Agenda agenda) {
        Actor cliente = theActorCalled(CLIENTE);
        LocalDate hoy = AgendaDePrueba.hoy();
        String completada = AgendaDePrueba.insertarReserva(bookingRepository, agenda, cliente,
                AgendaDePrueba.a(hoy.minusDays(10), 10), BookingStatus.COMPLETADA);
        String confirmadaPasada = AgendaDePrueba.insertarReserva(bookingRepository, agenda, cliente,
                AgendaDePrueba.a(hoy.minusDays(5), 15), BookingStatus.CONFIRMADA);
        Map<String, Booking> antes = new HashMap<>();
        antes.put(completada, reserva(completada));
        antes.put(confirmadaPasada, reserva(confirmadaPasada));
        contexto().remember(RESERVAS_ANTES, antes);
        guardarReservas(List.of(completada, confirmadaPasada));
    }

    private Booking reserva(String id) {
        return bookingRepository.findById(UUID.fromString(id)).orElseThrow(
                () -> new AssertionError("La reserva " + id + " ya no existe en el historial"));
    }
}
