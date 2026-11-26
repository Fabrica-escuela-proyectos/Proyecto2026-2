package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.models.DatosDeRegistro;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReservas;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarReservas;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;

import java.util.List;
import java.util.Map;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.agenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarAgenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarReservas;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.prepararAgendaYCliente;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.reservarPorLaApi;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.reservas;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/** HU-24 - Consultar reservas del negocio. Actores: el proveedor dueño, un cliente, otro proveedor y un usuario. */
public class ConsultarReservasDelNegocioSteps {

    private static final String SIN_RESERVAS = "No hay reservas registradas";
    private static final String SIN_PERMISO = "No tiene permisos";

    // ---------- Given ----------

    @Given("an authenticated Provider has registered services with active bookings")
    public void proveedorConReservasActivas() {
        Agenda agenda = prepararAgendaYCliente();
        Actor cliente = theActorCalled(CLIENTE);
        String primera = reservarPorLaApi(cliente, agenda, 7);
        String segunda = reservarPorLaApi(cliente, agenda, 8);
        guardarReservas(List.of(primera, segunda));
    }

    @Given("an authenticated Provider has no bookings in their services")
    @Given("an authenticated Provider wants to view bookings")
    public void proveedorAutenticado() {
        guardarAgenda(AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR)));
    }

    @Given("a user with the Client profile is authenticated")
    public void usuarioConPerfilCliente() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(USUARIO), "Cliente");
    }

    // ---------- When ----------

    @When("they request the list of bookings of their business")
    public void consultaLasReservasDeSuNegocio() {
        String negocio = agenda().idDelNegocio();
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarReservas.delNegocio(negocio));
    }

    @When("they try to access the bookings of another Provider's services")
    public void consultaLasReservasDeOtroNegocio() {
        Actor otro = theActorCalled(OTRO_PROVEEDOR);
        CuentasDePrueba.registrar(otro, "Proveedor");
        String negocioAjeno = otro.recall(Memoria.ID_DE_NEGOCIO);
        theActorCalled(PROVEEDOR).attemptsTo(ConsultarReservas.delNegocio(negocioAjeno));
    }

    /** El negocio existe y tiene dueño; quien consulta es el "Usuario" (sin sesión o con rol Cliente). */
    @When("they try to access the list of bookings of the business")
    public void intentaConsultarLasReservasDelNegocio() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrar(proveedor, "Proveedor");
        String negocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        theActorCalled(USUARIO).attemptsTo(ConsultarReservas.delNegocio(negocio));
    }

    // ---------- Then / And ----------

    @Then("they see all the bookings associated with their services")
    public void veTodasLasReservas() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        List<Object> ids = RespuestaDeReservas.reservasDeLaLista().answeredBy(proveedor).stream()
                .map(reserva -> reserva.get("id")).toList();
        assertThat(ids).containsExactlyInAnyOrderElementsOf(reservas());
    }

    @And("each booking shows the date, start time, end time, service and Client name")
    public void cadaReservaMuestraSusDatos() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        DatosDeRegistro cliente = theActorCalled(CLIENTE).recall(Memoria.DATOS);
        String servicio = agenda().nombreDelServicio();
        for (Map<String, Object> reserva : RespuestaDeReservas.reservasDeLaLista().answeredBy(proveedor)) {
            assertThat(reserva.get("date")).as("fecha").isNotNull();
            assertThat(reserva.get("startTime")).as("hora de inicio").isNotNull();
            assertThat(reserva.get("endTime")).as("hora de fin").isNotNull();
            assertThat(reserva.get("serviceName")).as("servicio").isEqualTo(servicio);
            assertThat(reserva.get("clientName")).as("nombre del cliente").isEqualTo(cliente.nombreCompleto());
        }
    }

    @Then("they see an empty list")
    public void veUnaListaVacia() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        assertThat(RespuestaDeReservas.reservasDeLaLista().answeredBy(proveedor)).isEmpty();
    }

    @And("the Provider is informed that there are no registered bookings")
    public void seLeInformaQueNoHayReservas() {
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeLaApi.mensaje(), equalTo(SIN_RESERVAS)));
    }

    @And("they receive a message saying they do not have permission to view them")
    public void mensajeDeQueNoTienePermiso() {
        theActorInTheSpotlight().should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(403)),
                seeThat(RespuestaDeLaApi.mensaje(), containsString(SIN_PERMISO)));
    }

    @Then("they are not allowed to access the feature")
    public void noSeLePermiteAcceder() {
        theActorCalled(USUARIO).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(401)));
    }

    /** La API no redirige: responde 401 para que el cliente (la aplicación) lleve al usuario al inicio de sesión. */
    @And("they are redirected to the login")
    public void esRedirigidoAlInicioDeSesion() {
        theActorCalled(USUARIO).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(401)));
    }

    @Then("access is denied because their profile does not have permission")
    public void seLeDeniegaPorSuPerfil() {
        theActorCalled(USUARIO).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(403)));
    }
}
