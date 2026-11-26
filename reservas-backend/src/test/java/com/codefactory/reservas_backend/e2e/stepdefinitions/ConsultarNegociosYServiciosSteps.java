package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.DatosDeRegistro;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDelCatalogo;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarCatalogo;
import com.codefactory.reservas_backend.e2e.tasks.CrearServicioConDescripcion;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.ID_DEL_NEGOCIO_CONSULTADO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.NOMBRES_DE_NEGOCIOS;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.SERVICIOS_ESPERADOS;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * HU-13 - Consultar negocios y servicios. Actores: el cliente que consulta, uno o dos proveedores dueños de los
 * negocios y un usuario sin sesión. "access is denied" y "they must authenticate again" ya están en otras clases.
 * Como la base se comparte entre escenarios, para "ver los negocios registrados" y "sin negocios" se vacía la tabla
 * de negocios antes de preparar el escenario (cada escenario crea sus propios datos, así que no afecta a los demás).
 */
public class ConsultarNegociosYServiciosSteps {

    private static final String SIN_NEGOCIOS = "Aún no hay negocios registrados";
    private static final String SIN_SERVICIOS = "Este negocio aún no tiene servicios disponibles";
    private static final String NEGOCIO_INEXISTENTE = "El negocio solicitado no existe";

    /** Lo que el proveedor creó, para compararlo con lo que ve el cliente. */
    private record ServicioEsperado(String nombre, String descripcion, int duracion, long precio) {
    }

    @Autowired
    private BusinessRepository negocios;

    // ---------- Given ----------

    /** Dos negocios (de dos proveedores) y un cliente con sesión, sobre un catálogo recién vaciado. */
    @Given("an authenticated Client wants to see the available businesses")
    public void clienteQuiereVerLosNegocios() {
        negocios.deleteAllInBatch();
        Actor uno = theActorCalled(PROVEEDOR);
        Actor otro = theActorCalled(OTRO_PROVEEDOR);
        CuentasDePrueba.registrar(uno, "Proveedor");
        CuentasDePrueba.registrar(otro, "Proveedor");
        List<String> nombres = List.of(nombreDelNegocioDe(uno), nombreDelNegocioDe(otro));
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(CLIENTE), "Cliente");
        contexto().remember(NOMBRES_DE_NEGOCIOS, nombres);
    }

    @Given("an authenticated Client")
    public void clienteAutenticado() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(CLIENTE), "Cliente");
    }

    @Given("no businesses are registered on the platform")
    public void noHayNegocios() {
        negocios.deleteAllInBatch();
    }

    /** El negocio ofrece dos servicios con descripción, duración y precio distintos. */
    @Given("an authenticated Client selects a business with registered services")
    public void clienteSeleccionaUnNegocioConServicios() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(proveedor, "Proveedor");
        String negocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        List<ServicioEsperado> servicios = List.of(
                new ServicioEsperado("Corte de cabello", "Corte y peinado", 45, 35_000),
                new ServicioEsperado("Manicure", "Manicure tradicional con esmalte", 60, 28_000));
        for (ServicioEsperado servicio : servicios) {
            proveedor.attemptsTo(CrearServicioConDescripcion.enElNegocio(negocio, servicio.nombre(),
                    servicio.descripcion(), servicio.duracion(), servicio.precio()));
            proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        }
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(CLIENTE), "Cliente");
        contexto().remember(ID_DEL_NEGOCIO_CONSULTADO, negocio);
        contexto().remember(SERVICIOS_ESPERADOS, servicios);
    }

    @Given("a Client selects a business without registered services")
    public void clienteSeleccionaUnNegocioSinServicios() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrar(proveedor, "Proveedor");
        String negocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(CLIENTE), "Cliente");
        contexto().remember(ID_DEL_NEGOCIO_CONSULTADO, negocio);
    }

    @Given("an authenticated Client requests a business that does not exist")
    public void clienteSolicitaUnNegocioInexistente() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(CLIENTE), "Cliente");
        contexto().remember(ID_DEL_NEGOCIO_CONSULTADO, UUID.randomUUID().toString());
    }

    @Given("a user does not have a valid session")
    public void usuarioSinSesionValida() {
        theActorCalled(USUARIO).remember(Memoria.TOKEN, "");
    }

    // ---------- When ----------

    @When("they access the catalog")
    public void accedeAlCatalogo() {
        theActorCalled(CLIENTE).attemptsTo(ConsultarCatalogo.listado());
    }

    @When("they check the details of the business")
    @When("they try to view its details")
    public void consultaElDetalleDelNegocio() {
        String negocio = contexto().recall(ID_DEL_NEGOCIO_CONSULTADO);
        theActorCalled(CLIENTE).attemptsTo(ConsultarCatalogo.detalleDe(negocio));
    }

    @When("they try to view the catalog")
    public void intentaConsultarElCatalogo() {
        theActorCalled(USUARIO).attemptsTo(ConsultarCatalogo.listado());
    }

    // ---------- Then / And ----------

    @Then("they see the registered businesses")
    public void veLosNegociosRegistrados() {
        Actor cliente = theActorCalled(CLIENTE);
        List<String> esperados = contexto().recall(NOMBRES_DE_NEGOCIOS);
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        assertThat(RespuestaDelCatalogo.negociosDelListado().answeredBy(cliente))
                .extracting(negocio -> negocio.get("name"))
                .containsExactlyInAnyOrderElementsOf(esperados);
    }

    @And("each business is identified by its name")
    public void cadaNegocioSeIdentificaPorSuNombre() {
        Actor cliente = theActorCalled(CLIENTE);
        for (Map<String, Object> negocio : RespuestaDelCatalogo.negociosDelListado().answeredBy(cliente)) {
            assertThat(negocio.get("id")).as("id del negocio").isNotNull();
            assertThat((String) negocio.get("name")).as("nombre del negocio").isNotBlank();
        }
    }

    @Then("they see the services it offers")
    public void veLosServiciosQueOfrece() {
        Actor cliente = theActorCalled(CLIENTE);
        List<ServicioEsperado> esperados = contexto().recall(SERVICIOS_ESPERADOS);
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        assertThat(RespuestaDelCatalogo.serviciosDelNegocio().answeredBy(cliente))
                .extracting(servicio -> servicio.get("name"))
                .containsExactlyInAnyOrderElementsOf(esperados.stream().map(ServicioEsperado::nombre).toList());
    }

    @And("each service includes name, description, duration and price")
    public void cadaServicioIncluyeSusDatos() {
        Actor cliente = theActorCalled(CLIENTE);
        List<ServicioEsperado> esperados = contexto().recall(SERVICIOS_ESPERADOS);
        for (Map<String, Object> servicio : RespuestaDelCatalogo.serviciosDelNegocio().answeredBy(cliente)) {
            ServicioEsperado esperado = esperados.stream()
                    .filter(e -> e.nombre().equals(servicio.get("name"))).findFirst().orElseThrow();
            assertThat(servicio.get("description")).as("descripción").isEqualTo(esperado.descripcion());
            assertThat(((Number) servicio.get("durationMinutes")).intValue()).as("duración").isEqualTo(esperado.duracion());
            assertThat(((Number) servicio.get("priceCop")).longValue()).as("precio").isEqualTo(esperado.precio());
        }
    }

    @Then("they see an empty catalog")
    public void veUnCatalogoVacio() {
        Actor cliente = theActorCalled(CLIENTE);
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        assertThat(RespuestaDelCatalogo.negociosDelListado().answeredBy(cliente)).isEmpty();
        assertThat(RespuestaDelCatalogo.totalDeNegocios().answeredBy(cliente)).isZero();
    }

    @And("they are informed that there are no businesses available yet")
    public void seInformaQueNoHayNegocios() {
        theActorCalled(CLIENTE).should(seeThat(RespuestaDeLaApi.mensaje(), equalTo(SIN_NEGOCIOS)));
    }

    @Then("they do not see any services")
    public void noVeServicios() {
        Actor cliente = theActorCalled(CLIENTE);
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        assertThat(RespuestaDelCatalogo.serviciosDelNegocio().answeredBy(cliente)).isEmpty();
    }

    @And("they are informed that the business does not offer services yet")
    public void seInformaQueNoOfreceServicios() {
        theActorCalled(CLIENTE).should(seeThat(RespuestaDeLaApi.mensaje(), equalTo(SIN_SERVICIOS)));
    }

    /** Un 404 no trae datos del negocio ni servicios: solo el error. */
    @Then("they do not see any information")
    public void noVeInformacion() {
        Actor cliente = theActorCalled(CLIENTE);
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(404)));
        assertThat(RespuestaDelCatalogo.nombreDelNegocio().answeredBy(cliente)).isNull();
        assertThat(RespuestaDelCatalogo.serviciosDelNegocio().answeredBy(cliente)).isNull();
    }

    @And("they are informed that the requested business does not exist")
    public void seInformaQueElNegocioNoExiste() {
        theActorCalled(CLIENTE).should(seeThat(RespuestaDeLaApi.mensaje(), equalTo(NEGOCIO_INEXISTENTE)));
    }

    // ---------- Apoyo ----------

    private static String nombreDelNegocioDe(Actor proveedor) {
        DatosDeRegistro datos = proveedor.recall(Memoria.DATOS);
        return datos.nombreDelNegocio();
    }
}
