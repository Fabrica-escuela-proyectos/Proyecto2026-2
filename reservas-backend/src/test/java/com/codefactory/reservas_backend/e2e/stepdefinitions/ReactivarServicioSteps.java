package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.ElNegocio;
import com.codefactory.reservas_backend.e2e.questions.ElServicio;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.DesactivarServicio;
import com.codefactory.reservas_backend.e2e.tasks.ReactivarServicio;
import com.codefactory.reservas_backend.e2e.tasks.ReservarServicio;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.MemoriaDeServicios;
import com.codefactory.reservas_backend.e2e.user.ServiciosDePrueba;
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

/** HU-12 - Reactivar servicio. El servicio es del proveedor dueño (PROVEEDOR_EXISTENTE). */
public class ReactivarServicioSteps {

    @Autowired
    private BookingRepository bookingRepository;

    // ---------- Given ----------

    /** Servicio que puede recibir reservas, desactivado; se guarda cómo era para compararlo después. */
    @Given("a deactivated service keeps its registered information")
    public void servicioDesactivadoConSuInformacion() {
        prepararServicioConAgenda();
        desactivar(false);
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.remember(MemoriaDeServicios.SERVICIO_ANTES, ServiciosDePrueba.servicioGuardado(ServiciosDePrueba.servicioDelDueno()));
    }

    @Given("a service was deactivated and its future bookings were cancelled with the reason {string}")
    public void servicioDesactivadoConReservasCanceladas(String motivo) {
        Agenda agenda = prepararServicioConAgenda();
        Actor cliente = ServiciosDePrueba.cliente();
        List<String> reservas = new ArrayList<>();
        reservas.add(AgendaDePrueba.reservar(cliente, agenda, AgendaDePrueba.horarioEn(2)));
        reservas.add(AgendaDePrueba.reservar(cliente, agenda, AgendaDePrueba.horarioEn(3)));
        ServiciosDePrueba.dueno().remember(MemoriaDeServicios.RESERVAS, reservas);
        desactivar(true);
        reservas.forEach(id -> assertThat(bookingRepository.findById(UUID.fromString(id)).orElseThrow().getCancelReason())
                .containsIgnoringCase(motivo));
    }

    @Given("a service was reactivated successfully")
    public void servicioReactivado() {
        prepararServicioConAgenda();
        desactivar(false);
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.attemptsTo(ReactivarServicio.conId(ServiciosDePrueba.servicioDelDueno()));
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    @And("there is a deactivated service that belongs to another provider")
    public void servicioDesactivadoDeOtroProveedor() {
        ServiciosDePrueba.crearServicioValido();
        desactivar(false);
    }

    // ---------- When ----------

    @When("the provider asks to reactivate the service")
    @When("the provider reactivates it")
    public void pideReactivar() {
        ServiciosDePrueba.dueno().attemptsTo(ReactivarServicio.conId(ServiciosDePrueba.servicioDelDueno()));
    }

    @When("they try to reactivate that service")
    public void otroProveedorIntentaReactivar() {
        String servicioAjeno = ServiciosDePrueba.servicioDelDueno();
        theActorCalled(OTRO_PROVEEDOR).attemptsTo(ReactivarServicio.conId(servicioAjeno));
    }

    /** Lo usan el cliente y el usuario sin sesión: actúa quien esté en el foco después del Given. */
    @When("they try to reactivate a service")
    public void intentaReactivarUnServicio() {
        Actor quienIntenta = theActorInTheSpotlight();
        String servicio = ServiciosDePrueba.crearServicioValido();
        ServiciosDePrueba.enElFoco(quienIntenta).attemptsTo(ReactivarServicio.conId(servicio));
    }

    // ---------- Then / And ----------

    /** "Active" en el feature = el campo active en true. */
    @Then("the service is in status {string}")
    public void elServicioQuedaEnEstado(String estado) {
        ServiciosDePrueba.dueno().should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(ElServicio.estaActivo(), is("Active".equalsIgnoreCase(estado))));
    }

    @And("the service is available for new bookings again")
    @And("a new booking can be made if it meets the availability and advance notice rules")
    public void sePuedeReservarDeNuevo() {
        String servicio = ServiciosDePrueba.servicioDelDueno();
        Actor cliente = ServiciosDePrueba.cliente();
        cliente.attemptsTo(ReservarServicio.en(servicio, AgendaDePrueba.horarioEn(4), AgendaDePrueba.DURACION));
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
    }

    @Then("it is the same service with its registered information")
    public void esElMismoServicio() {
        Actor dueno = ServiciosDePrueba.dueno();
        Map<String, Object> antes = dueno.recall(MemoriaDeServicios.SERVICIO_ANTES);
        dueno.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(ElServicio.id(), equalTo(String.valueOf(antes.get("id")))),
                seeThat(ElServicio.nombre(), equalTo(String.valueOf(antes.get("name")))));
        Map<String, Object> ahora = ServiciosDePrueba.servicioGuardado(ServiciosDePrueba.servicioDelDueno());
        for (String campo : new String[]{"name", "description", "durationMinutes", "priceCop", "createdAt"}) {
            assertThat(ahora.get(campo)).as(campo).isEqualTo(antes.get(campo));
        }
    }

    /** El negocio sigue con un solo servicio con ese nombre: no se creó otro al reactivar. */
    @And("no duplicate service is created")
    public void noSeCreaUnDuplicado() {
        Map<String, Object> ahora = ServiciosDePrueba.servicioGuardado(ServiciosDePrueba.servicioDelDueno());
        assertThat(ServiciosDePrueba.serviciosLlamados(String.valueOf(ahora.get("name")))).isEqualTo(1L);
    }

    @And("the bookings cancelled before stay in status {string} and are not restored automatically")
    public void lasReservasSiguenCanceladas(String estado) {
        List<String> ids = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.RESERVAS);
        ids.forEach(id -> assertThat(bookingRepository.findById(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(DesactivarServicioSteps.estadoEnLaBase(estado)));
    }

    @Then("the service appears again among the available services")
    public void apareceDeNuevoEnElCatalogo() {
        String servicio = ServiciosDePrueba.servicioDelDueno();
        List<String> visibles = ElNegocio.idsDeLosServicios().answeredBy(ServiciosDePrueba.cliente());
        assertThat(visibles).as("servicios que ve el cliente").contains(servicio);
    }

    @And("the service stays deactivated")
    public void sigueDesactivado() {
        Map<String, Object> guardado = ServiciosDePrueba.servicioGuardado(ServiciosDePrueba.servicioDelDueno());
        assertThat(guardado.get("active")).as("el servicio sigue desactivado").isEqualTo(false);
    }

    // ---------- Apoyo ----------

    private Agenda prepararServicioConAgenda() {
        Actor dueno = theActorCalled(PROVEEDOR_EXISTENTE);
        Agenda agenda = AgendaDePrueba.prepararNegocio(dueno);
        dueno.remember(MemoriaDeServicios.AGENDA, agenda);
        dueno.remember(MemoriaDeServicios.ID_DE_SERVICIO, agenda.idDelServicio());
        dueno.remember(MemoriaDeServicios.DURACION, agenda.duracionEnMinutos());
        return agenda;
    }

    /** El dueño desactiva su servicio (confirmando si tiene reservas futuras) y se comprueba que quedó inactivo. */
    private void desactivar(boolean confirmando) {
        Actor dueno = ServiciosDePrueba.dueno();
        String servicio = ServiciosDePrueba.servicioDelDueno();
        dueno.attemptsTo(confirmando ? DesactivarServicio.confirmando(servicio) : DesactivarServicio.sinConfirmar(servicio));
        dueno.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(ElServicio.estaActivo(), is(false)));
    }
}
