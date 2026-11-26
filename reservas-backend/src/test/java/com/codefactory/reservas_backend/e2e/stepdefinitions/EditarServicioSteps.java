package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.ElServicio;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.CambiarPrecioDelServicio;
import com.codefactory.reservas_backend.e2e.tasks.EditarServicio;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.PROVEEDOR_EXISTENTE;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;

/** HU-10 - Editar servicio. El servicio es del proveedor dueño (PROVEEDOR_EXISTENTE). */
public class EditarServicioSteps {

    @Autowired
    private BookingRepository bookingRepository;

    // ---------- Given ----------

    /** Prepara un negocio que puede recibir reservas, con un servicio de ese precio. */
    @Given("a provider has a service with a price of {int} COP")
    public void servicioConPrecio(int precio) {
        Actor dueno = theActorCalled(PROVEEDOR_EXISTENTE);
        Agenda agenda = AgendaDePrueba.prepararNegocio(dueno, precio);
        dueno.remember(MemoriaDeServicios.AGENDA, agenda);
        dueno.remember(MemoriaDeServicios.ID_DE_SERVICIO, agenda.idDelServicio());
    }

    @And("there is a booking for that service with a price of {int} COP")
    public void hayUnaReservaConEsePrecio(int precio) {
        Agenda agenda = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.AGENDA);
        String reserva = AgendaDePrueba.reservar(ServiciosDePrueba.cliente(), agenda, AgendaDePrueba.horarioEn(2));
        assertThat(bookingRepository.findById(UUID.fromString(reserva)).orElseThrow().getPriceCop()).isEqualTo(precio);
        ServiciosDePrueba.dueno().remember(MemoriaDeServicios.RESERVAS, List.of(reserva));
    }

    @Given("a provider has two services called {string} and {string}")
    public void dosServicios(String primero, String segundo) {
        Map<String, String> porNombre = new HashMap<>();
        for (String nombre : new String[]{primero, segundo}) {
            Map<String, Object> datos = ServiciosDePrueba.datosValidos();
            datos.put("name", nombre);
            porNombre.put(nombre, ServiciosDePrueba.crearServicio(datos));
        }
        ServiciosDePrueba.dueno().remember(MemoriaDeServicios.SERVICIOS_POR_NOMBRE, porNombre);
    }

    // ---------- When ----------

    @When("they change the name, description, duration and price of the service with valid data")
    public void cambiaTodosLosDatos() {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("name", "Servicio editado " + UUID.randomUUID().toString().substring(0, 6));
        cambios.put("description", "Incluye una sesion de seguimiento");
        cambios.put("durationMinutes", 45);
        cambios.put("priceCop", 65_000);
        editar(cambios);
    }

    @When("they change only the description of the service")
    public void cambiaSoloLaDescripcion() {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("description", "Nueva descripcion del servicio");
        editar(cambios);
    }

    /** Reutiliza la tarea del equipo que cambia solo el precio (la hicieron para la HU-33). */
    @When("the provider changes the price of the service to {int} COP")
    public void cambiaElPrecioConLaTareaDelEquipo(int precio) {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.attemptsTo(CambiarPrecioDelServicio.a(ServiciosDePrueba.servicioDelDueno(), precio));
    }

    @When("they change the price of the service to {int} COP")
    public void cambiaElPrecio(int precio) {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("priceCop", precio);
        editar(cambios);
    }

    @When("they change the {string} of the service to {string}")
    public void cambiaUnCampo(String campo, String valor) {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put(ServiciosDePrueba.campoEnLaApi(campo), "name".equals(campo) ? valor : ServiciosDePrueba.valor(valor));
        editar(cambios);
    }

    @When("they change the name of {string} to {string}")
    public void cambiaElNombre(String actual, String nuevo) {
        Actor dueno = ServiciosDePrueba.dueno();
        Map<String, String> porNombre = dueno.recall(MemoriaDeServicios.SERVICIOS_POR_NOMBRE);
        String id = porNombre.get(actual);
        dueno.remember(MemoriaDeServicios.ID_DE_SERVICIO, id);
        dueno.remember(MemoriaDeServicios.SERVICIO_ANTES, ServiciosDePrueba.servicioGuardado(id));
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("name", nuevo);
        editar(cambios);
    }

    @When("they try to modify that service")
    public void otroProveedorIntentaModificar() {
        String servicioAjeno = ServiciosDePrueba.servicioDelDueno();
        theActorCalled(OTRO_PROVEEDOR).attemptsTo(EditarServicio.conLosDatos(servicioAjeno, Map.<String, Object>of("priceCop", 1)));
    }

    /** Lo usan el cliente y el usuario sin sesión: actúa quien esté en el foco después del Given. */
    @When("they try to modify a service")
    public void intentaModificarUnServicio() {
        Actor quienIntenta = theActorInTheSpotlight();
        String servicio = ServiciosDePrueba.crearServicioValido();
        ServiciosDePrueba.enElFoco(quienIntenta).attemptsTo(EditarServicio.conLosDatos(servicio, Map.<String, Object>of("priceCop", 1)));
    }

    // ---------- Then / And ----------

    @Then("the service information is updated")
    @Then("the change is accepted")
    public void elCambioEsAceptado() {
        ServiciosDePrueba.dueno().should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    /** Lo que el proveedor envió quedó guardado en el servicio. */
    @And("the provider sees the new data saved")
    public void veLosNuevosDatos() {
        Map<String, Object> esperados = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.DATOS_ESPERADOS);
        Map<String, Object> guardado = ServiciosDePrueba.servicioGuardado(ServiciosDePrueba.servicioDelDueno());
        esperados.forEach((campo, valor) ->
                assertThat(String.valueOf(guardado.get(campo))).as(campo).isEqualTo(String.valueOf(valor)));
    }

    @And("the name, duration and price of the service stay the same")
    public void losDemasDatosNoCambian() {
        Map<String, Object> antes = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.SERVICIO_ANTES);
        Map<String, Object> guardado = ServiciosDePrueba.servicioGuardado(ServiciosDePrueba.servicioDelDueno());
        for (String campo : new String[]{"name", "durationMinutes", "priceCop"}) {
            assertThat(guardado.get(campo)).as(campo).isEqualTo(antes.get(campo));
        }
    }

    @Then("the service has a price of {int} COP")
    public void elServicioTieneElPrecio(int precio) {
        ServiciosDePrueba.dueno().should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(ElServicio.precio(), equalTo((long) precio)));
    }

    @And("the existing booking keeps the price of {int} COP")
    public void laReservaConservaElPrecio(int precio) {
        List<String> reservas = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.RESERVAS);
        assertThat(bookingRepository.findById(UUID.fromString(reservas.get(0))).orElseThrow().getPriceCop())
                .as("precio guardado en la reserva").isEqualTo(precio);
    }

    /** Rechazado = error del cliente (400 datos inválidos, 409 nombre repetido) y el servicio igual que antes. */
    @Then("the change is rejected and the service keeps its previous data")
    public void elCambioEsRechazado() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), allOf(greaterThanOrEqualTo(400), lessThan(500))));
        Map<String, Object> antes = dueno.recall(MemoriaDeServicios.SERVICIO_ANTES);
        assertThat(ServiciosDePrueba.servicioGuardado(ServiciosDePrueba.servicioDelDueno())).isEqualTo(antes);
    }

    @And("the information of the service is not modified")
    public void laInformacionNoCambia() {
        Map<String, Object> antes = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.SERVICIO_ANTES);
        assertThat(ServiciosDePrueba.servicioGuardado(ServiciosDePrueba.servicioDelDueno())).isEqualTo(antes);
    }

    // ---------- Apoyo ----------

    /** Guarda lo que se espera ver después y el dueño envía la edición. */
    private void editar(Map<String, Object> cambios) {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.remember(MemoriaDeServicios.DATOS_ESPERADOS, cambios);
        dueno.attemptsTo(EditarServicio.conLosDatos(ServiciosDePrueba.servicioDelDueno(), cambios));
    }
}
