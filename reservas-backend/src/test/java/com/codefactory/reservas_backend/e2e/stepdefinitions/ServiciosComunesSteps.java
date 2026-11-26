package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarNegocio;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.MemoriaDeServicios;
import com.codefactory.reservas_backend.e2e.user.ServiciosDePrueba;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import com.codefactory.reservas_backend.service.infrastructure.ServiceOfferingRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import static com.codefactory.reservas_backend.e2e.user.Personajes.CLIENTE_EXISTENTE;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;

/**
 * Pasos que comparten mis HU (HU-07 a HU-12). Cucumber no permite dos métodos con la misma frase, así que cada
 * frase que se repite entre features está aquí una sola vez.
 */
public class ServiciosComunesSteps {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private ServiceOfferingRepository serviceOfferingRepository;

    /** Antes de cada escenario le paso a ServiciosDePrueba los repositorios para revisar cómo quedan los datos. */
    @Before
    public void conectarLaBaseDeDatos() {
        ServiciosDePrueba.usarLaBaseDeDatos(serviceOfferingRepository, businessRepository);
    }

    // ---------- Given ----------

    @Given("a provider is logged in and owns a business")
    public void proveedorConSesionYNegocio() {
        ServiciosDePrueba.dueno();
    }

    @Given("a provider has a service in their business")
    public void proveedorConUnServicio() {
        String id = ServiciosDePrueba.crearServicioValido();
        ServiciosDePrueba.dueno().remember(MemoriaDeServicios.SERVICIO_ANTES, ServiciosDePrueba.servicioGuardado(id));
    }

    /** El que actúa es "otro proveedor": tiene sesión, pero el negocio o el servicio son del dueño. */
    @Given("a provider is logged in")
    public void otroProveedorConSesion() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(OTRO_PROVEEDOR), "Proveedor");
    }

    @Given("a user with the Client role is logged in")
    public void clienteConSesion() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(CLIENTE_EXISTENTE), "Cliente");
    }

    @Given("there is a service that belongs to another provider")
    public void servicioDeOtroProveedor() {
        proveedorConUnServicio();
    }

    @Given("a provider has more than one business")
    public void proveedorConVariosNegocios() {
        Actor dueno = ServiciosDePrueba.dueno();
        String otroNegocio = ServiciosDePrueba.crearSegundoNegocio(businessRepository, ServiciosDePrueba.negocioDelDueno());
        dueno.remember(MemoriaDeServicios.OTRO_NEGOCIO, otroNegocio);
    }

    // ---------- When ----------

    @When("a client checks the services of that business")
    @When("a client checks that business")
    public void elClienteConsultaElNegocio() {
        String negocio = ServiciosDePrueba.negocioDelDueno();
        Actor cliente = ServiciosDePrueba.cliente();
        cliente.attemptsTo(ConsultarNegocio.conId(negocio));
    }

    // ---------- Then ----------

    @Then("they receive an error message about the field {string}")
    public void mensajeDeErrorDelCampo(String campo) {
        ServiciosDePrueba.dueno().should(seeThat(
                RespuestaDeLaApi.mensajeDelCampo(ServiciosDePrueba.campoEnLaApi(campo)), not(emptyOrNullString())));
    }

    @And("they receive a message saying a service with that name already exists")
    public void mensajeDeNombreRepetido() {
        ServiciosDePrueba.dueno().should(seeThat(RespuestaDeLaApi.mensaje(),
                containsString("Ya existe un servicio con ese nombre")));
    }

    /** La reserva no se crea: la API responde con un error del cliente (4xx). */
    @Then("the booking is rejected")
    public void laReservaEsRechazada() {
        ServiciosDePrueba.cliente().should(seeThat(RespuestaDeLaApi.codigoDeEstado(),
                allOf(greaterThanOrEqualTo(400), lessThan(500))));
    }
}
