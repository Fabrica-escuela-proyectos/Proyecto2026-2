package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.audit.infrastructure.AuditLogRepository;
import com.codefactory.reservas_backend.e2e.models.DatosDeRegistro;
import com.codefactory.reservas_backend.e2e.models.Horario;
import com.codefactory.reservas_backend.e2e.questions.ElNegocio;
import com.codefactory.reservas_backend.e2e.questions.ElServicio;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarServicio;
import com.codefactory.reservas_backend.e2e.tasks.ReservarServicio;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.e2e.user.MemoriaDeServicios;
import com.codefactory.reservas_backend.e2e.user.ServiciosDePrueba;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.nullValue;

/**
 * HU-09 - Crear servicio. El proveedor dueño es PROVEEDOR_EXISTENTE; los pasos que comparten otras HU
 * ("a provider is logged in and owns a business", "the booking is rejected"...) están en ServiciosComunesSteps.
 */
public class CrearServicioSteps {

    @Autowired
    private AuditLogRepository auditLogRepository;

    // ---------- Given ----------

    @Given("a provider created an active service")
    @Given("a provider created a service that has no resources assigned")
    public void proveedorCreoUnServicio() {
        ServiciosDePrueba.crearServicioValido();
    }

    @Given("a business already has a service called {string}")
    public void negocioConUnServicioLlamado(String nombre) {
        Map<String, Object> datos = ServiciosDePrueba.datosValidos();
        datos.put("name", nombre);
        ServiciosDePrueba.crearServicio(datos);
    }

    /** La creación ya ocurrió aquí; el When de la auditoría solo confirma que quedó guardado. */
    @Given("a provider created a service")
    public void proveedorCreoUnServicioParaLaAuditoria() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.attemptsTo(RegistrarServicio.conLosDatos(ServiciosDePrueba.negocioDelDueno(), ServiciosDePrueba.datosValidos()));
    }

    // ---------- When ----------

    @When("they register a service with a valid name, duration and price")
    public void registraUnServicioValido() {
        registrar(ServiciosDePrueba.datosValidos());
    }

    @When("they register a service with a price of 0 pesos")
    public void registraUnServicioGratis() {
        Map<String, Object> datos = ServiciosDePrueba.datosValidos();
        datos.put("priceCop", 0);
        registrar(datos);
    }

    @When("they register a valid service with the description {string}")
    public void registraConDescripcion(String descripcion) {
        Map<String, Object> datos = ServiciosDePrueba.datosValidos();
        datos.put("description", descripcion);
        registrar(datos);
    }

    @When("they register a valid service without a description")
    public void registraSinDescripcion() {
        registrar(ServiciosDePrueba.datosValidos());
    }

    @When("they register a service leaving the field {string} empty")
    public void registraSinUnCampo(String campo) {
        Map<String, Object> datos = ServiciosDePrueba.datosValidos();
        datos.remove(ServiciosDePrueba.campoEnLaApi(campo));
        registrar(datos);
    }

    @When("they register a service with the duration {string}")
    public void registraConLaDuracion(String duracion) {
        Map<String, Object> datos = ServiciosDePrueba.datosValidos();
        datos.put("durationMinutes", ServiciosDePrueba.valor(duracion));
        registrar(datos);
    }

    @When("they register a service with the price {string}")
    public void registraConElPrecio(String precio) {
        Map<String, Object> datos = ServiciosDePrueba.datosValidos();
        datos.put("priceCop", ServiciosDePrueba.valor(precio));
        registrar(datos);
    }

    /** Hallazgo: un texto donde va un número. */
    @When("they register a service with the {string} {string}")
    public void registraConUnTextoEnElCampo(String campo, String texto) {
        Map<String, Object> datos = ServiciosDePrueba.datosValidos();
        datos.put(ServiciosDePrueba.campoEnLaApi(campo), texto);
        registrar(datos);
    }

    @When("they register a service whose {string} has {int} characters")
    public void registraConUnTextoLargo(String campo, int caracteres) {
        Map<String, Object> datos = ServiciosDePrueba.datosValidos();
        datos.put(ServiciosDePrueba.campoEnLaApi(campo), "a".repeat(caracteres));
        registrar(datos);
    }

    @When("its provider tries to create another service called {string}")
    public void intentaCrearOtroConElMismoNombre(String nombre) {
        Map<String, Object> datos = ServiciosDePrueba.datosValidos();
        datos.put("name", nombre);
        registrar(datos);
    }

    @When("they try to create a service in a business that belongs to another provider")
    public void otroProveedorIntentaCrear() {
        String negocioAjeno = ServiciosDePrueba.negocioDelDueno();
        ServiciosDePrueba.dueno().remember(MemoriaDeServicios.CANTIDAD_ANTES, ServiciosDePrueba.cantidadDeServicios());
        theActorCalled(OTRO_PROVEEDOR)
                .attemptsTo(RegistrarServicio.conLosDatos(negocioAjeno, ServiciosDePrueba.datosValidos()));
    }

    /** Lo usan el cliente y el usuario sin sesión: actúa quien esté en el foco después del Given. */
    @When("they try to create a service in a business")
    public void intentaCrearUnServicio() {
        Actor quienIntenta = theActorInTheSpotlight();
        String negocio = ServiciosDePrueba.negocioDelDueno();
        ServiciosDePrueba.enElFoco(quienIntenta)
                .attemptsTo(RegistrarServicio.conLosDatos(negocio, ServiciosDePrueba.datosValidos()));
    }

    @When("a client tries to book that service")
    public void elClienteIntentaReservar() {
        String servicio = ServiciosDePrueba.servicioDelDueno();
        Integer duracion = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.DURACION);
        Horario horario = AgendaDePrueba.horarioEn(2);
        ServiciosDePrueba.cliente().attemptsTo(ReservarServicio.en(servicio, horario, duracion));
    }

    @When("the service is saved")
    public void elServicioQuedaGuardado() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        dueno.remember(MemoriaDeServicios.ID_DE_SERVICIO, ElServicio.id().answeredBy(dueno));
    }

    // ---------- Then / And ----------

    @Then("the service is created in their business")
    public void elServicioQuedaCreado() {
        Actor dueno = ServiciosDePrueba.dueno();
        String idDelNegocio = dueno.recall(Memoria.ID_DE_NEGOCIO);
        dueno.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)),
                seeThat(ElServicio.idDelNegocio(), equalTo(idDelNegocio)));
    }

    @And("the service is active")
    public void elServicioEstaActivo() {
        ServiciosDePrueba.dueno().should(seeThat(ElServicio.estaActivo(), is(true)));
    }

    @Then("the service is created as free")
    public void elServicioQuedaGratis() {
        ServiciosDePrueba.dueno().should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)),
                seeThat(ElServicio.precio(), equalTo(0L)));
    }

    @And("the service keeps the description {string}")
    public void conservaLaDescripcion(String descripcion) {
        ServiciosDePrueba.dueno().should(seeThat(ElServicio.descripcion(), equalTo(descripcion)));
    }

    @And("the service has no description")
    public void notieneDescripcion() {
        ServiciosDePrueba.dueno().should(seeThat(ElServicio.descripcion(), nullValue()));
    }

    @Then("they see the service with its duration and its price in pesos")
    public void veElServicioConDuracionYPrecio() {
        String servicio = ServiciosDePrueba.servicioDelDueno();
        Map<String, Object> enElCatalogo = ElNegocio.elServicio(servicio).answeredBy(ServiciosDePrueba.cliente());
        assertThat(enElCatalogo).as("el servicio aparece en el catálogo del negocio").isNotNull();
        assertThat(((Number) enElCatalogo.get("durationMinutes")).intValue()).isEqualTo(ServiciosDePrueba.DURACION_VALIDA);
        assertThat(((Number) enElCatalogo.get("priceCop")).longValue()).isEqualTo(ServiciosDePrueba.PRECIO_VALIDO);
    }

    @And("they receive a message saying the service has no availability yet")
    public void mensajeDeSinDisponibilidad() {
        ServiciosDePrueba.cliente().should(seeThat(RespuestaDeLaApi.mensaje(), containsString("no está disponible")));
    }

    /** Rechazado = 400 y el negocio sigue con los mismos servicios que tenía antes del intento. */
    @Then("the request is rejected and the service is not created")
    public void laSolicitudEsRechazada() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)));
        Integer antes = dueno.recall(MemoriaDeServicios.CANTIDAD_ANTES);
        assertThat(ServiciosDePrueba.cantidadDeServicios()).as("servicios del negocio").isEqualTo(antes);
    }

    @And("they receive a message saying the duration is not valid")
    public void mensajeDeDuracionInvalida() {
        ServiciosDePrueba.dueno().should(seeThat(RespuestaDeLaApi.mensajeDelCampo("durationMinutes"),
                not(emptyOrNullString())));
    }

    @And("they receive a message saying the price is not valid")
    public void mensajeDePrecioInvalido() {
        ServiciosDePrueba.dueno().should(seeThat(RespuestaDeLaApi.mensajeDelCampo("priceCop"),
                not(emptyOrNullString())));
    }

    @Then("the request is rejected and no duplicate service is created")
    public void noSeCreaUnDuplicado() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(409)));
        Integer antes = dueno.recall(MemoriaDeServicios.CANTIDAD_ANTES);
        assertThat(ServiciosDePrueba.cantidadDeServicios()).as("servicios del negocio").isEqualTo(antes);
    }

    @Then("access is denied and the service is not created")
    public void accesoDenegadoYNoSeCrea() {
        theActorCalled(OTRO_PROVEEDOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(403)));
        Integer antes = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.CANTIDAD_ANTES);
        assertThat(ServiciosDePrueba.cantidadDeServicios()).as("servicios del negocio ajeno").isEqualTo(antes);
    }

    @Then("it is recorded who created it and when")
    public void quedaRegistradoQuienYCuando() {
        Actor dueno = ServiciosDePrueba.dueno();
        DatosDeRegistro datos = dueno.recall(Memoria.DATOS);
        String servicio = dueno.recall(MemoriaDeServicios.ID_DE_SERVICIO);
        assertThat(auditLogRepository.findAll())
                .as("registro de auditoría de la creación del servicio")
                .anySatisfy(registro -> {
                    assertThat(registro.getEventType()).isEqualTo(AuditEventType.CREACION_SERVICIO);
                    assertThat(registro.getSubjectEmail()).isEqualTo(datos.correo());
                    assertThat(registro.getDetail()).contains(servicio);
                    assertThat(registro.getCreatedAt()).isNotNull();
                });
    }

    // ---------- Apoyo ----------

    /** Guarda cuántos servicios tiene el negocio y luego el dueño envía el servicio (sin comprobar nada aún). */
    private void registrar(Map<String, Object> datos) {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.remember(MemoriaDeServicios.CANTIDAD_ANTES, ServiciosDePrueba.cantidadDeServicios());
        ServiciosDePrueba.enElFoco(dueno).attemptsTo(RegistrarServicio.conLosDatos(ServiciosDePrueba.negocioDelDueno(), datos));
    }
}
