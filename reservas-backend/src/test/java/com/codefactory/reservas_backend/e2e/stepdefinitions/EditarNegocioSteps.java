package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.audit.infrastructure.AuditLogRepository;
import com.codefactory.reservas_backend.e2e.models.DatosDeRegistro;
import com.codefactory.reservas_backend.e2e.questions.ElNegocio;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.EditarNegocio;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.e2e.user.MemoriaDeServicios;
import com.codefactory.reservas_backend.e2e.user.ServiciosDePrueba;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * HU-07 - Editar información del negocio. Cómo quedó guardado el negocio se revisa en la base de datos
 * (ServiciosDePrueba.negocioGuardado); lo que ve un cliente se revisa en el catálogo.
 */
public class EditarNegocioSteps {

    private static final String MENSAJE_DE_EXITO = "La información del negocio fue actualizada";

    @Autowired
    private AuditLogRepository auditLogRepository;

    // ---------- Given ----------

    @Given("a provider updated the information of their business")
    @Given("a provider is logged in and owns a business with all its data filled in")
    public void negocioConTodosLosDatos() {
        Actor dueno = ServiciosDePrueba.dueno();
        Map<String, Object> datos = todosLosDatos();
        dueno.attemptsTo(EditarNegocio.conLosDatos(ServiciosDePrueba.negocioDelDueno(), datos));
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        dueno.remember(MemoriaDeServicios.DATOS_ESPERADOS, datos);
    }

    /** Se guarda cómo está el negocio antes del intento, para comprobar que no cambie. */
    @Given("a provider is editing their business")
    @Given("a provider is editing their business with the other data valid")
    public void proveedorEditandoSuNegocio() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.remember(MemoriaDeServicios.NEGOCIO_ANTES, ServiciosDePrueba.negocioGuardado(ServiciosDePrueba.negocioDelDueno()));
    }

    /** La edición ya ocurrió aquí; el When de la auditoría solo confirma que quedó guardada. */
    @Given("a provider modified the information of their business")
    public void proveedorModificoSuNegocio() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.attemptsTo(EditarNegocio.conLosDatos(ServiciosDePrueba.negocioDelDueno(), todosLosDatos()));
    }

    // ---------- When ----------

    @When("they change the name, description, address, business type and phone with valid data")
    @When("they change the information of one of them")
    public void cambiaTodosLosDatos() {
        guardarElOtroNegocioSiExiste();
        editar(todosLosDatos());
    }

    @When("they change only the address with a valid value")
    public void cambiaSoloLaDireccion() {
        proveedorEditandoSuNegocio();
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("address", "Carrera 70 # 52-21, Medellin");
        editar(cambios);
    }

    @When("they leave the address, the business type and the phone empty")
    public void dejaVaciosLosOpcionales() {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("address", "");
        cambios.put("businessType", "");
        cambios.put("phone", "");
        editar(cambios);
    }

    @When("they enter the phone {string}")
    public void ingresaElTelefono(String telefono) {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("phone", telefono);
        editar(cambios);
    }

    @When("they choose the business type {string}")
    public void eligeElTipo(String tipo) {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("businessType", tipo);
        editar(cambios);
    }

    @When("they leave the name of the business empty")
    public void dejaVacioElNombre() {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("name", "");
        editar(cambios);
    }

    @When("they change the name of the business to one of {int} characters")
    public void cambiaElNombreConCaracteres(int caracteres) {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("name", "N".repeat(caracteres));
        editar(cambios);
    }

    @When("they try to modify a business that belongs to another provider")
    public void otroProveedorIntentaModificar() {
        proveedorEditandoSuNegocio();
        String negocioAjeno = ServiciosDePrueba.negocioDelDueno();
        theActorCalled(OTRO_PROVEEDOR).attemptsTo(EditarNegocio.conLosDatos(negocioAjeno, todosLosDatos()));
    }

    /** Lo usan el cliente y el usuario sin sesión: actúa quien esté en el foco después del Given. */
    @When("they try to modify the information of a business")
    public void intentaModificarUnNegocio() {
        Actor quienIntenta = theActorInTheSpotlight();
        String negocio = ServiciosDePrueba.negocioDelDueno();
        ServiciosDePrueba.enElFoco(quienIntenta).attemptsTo(EditarNegocio.conLosDatos(negocio, todosLosDatos()));
    }

    @When("the change is saved")
    public void laEdicionQuedaGuardada() {
        ServiciosDePrueba.dueno().should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    // ---------- Then / And ----------

    /** 200 y, en el catálogo, cada dato enviado quedó guardado (un texto vacío queda sin valor). */
    @Then("the changes are saved")
    public void losCambiosQuedanGuardados() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        Map<String, Object> enviados = dueno.recall(MemoriaDeServicios.DATOS_ESPERADOS);
        Map<String, String> guardados = ServiciosDePrueba.negocioGuardado(ServiciosDePrueba.negocioDelDueno());
        enviados.forEach((campo, valor) -> assertThat(guardados.get(campo)).as(campo).isEqualTo(comoQuedaGuardado(campo, valor)));
    }

    @And("they receive the confirmation that the information was updated")
    public void confirmacionDeActualizacion() {
        ServiciosDePrueba.dueno().should(seeThat(RespuestaDeLaApi.mensaje(), equalTo(MENSAJE_DE_EXITO)));
    }

    @Then("the change is saved and the other data of the business stay the same")
    public void soloCambiaLaDireccion() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        Map<String, String> antes = dueno.recall(MemoriaDeServicios.NEGOCIO_ANTES);
        Map<String, Object> enviados = dueno.recall(MemoriaDeServicios.DATOS_ESPERADOS);
        Map<String, String> guardados = ServiciosDePrueba.negocioGuardado(ServiciosDePrueba.negocioDelDueno());
        ElNegocio.DATOS.forEach(campo -> {
            String esperado = enviados.containsKey(campo) ? String.valueOf(enviados.get(campo)) : antes.get(campo);
            assertThat(guardados.get(campo)).as(campo).isEqualTo(esperado);
        });
    }

    @Then("they see the updated name, description, address, business type and phone")
    public void elClienteVeLosDatosNuevos() {
        Map<String, Object> enviados = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.DATOS_ESPERADOS);
        Actor cliente = ServiciosDePrueba.cliente();
        enviados.forEach((campo, valor) -> cliente.should(
                seeThat(ElNegocio.dato(campo), equalTo(comoQuedaGuardado(campo, valor)))));
    }

    @And("the business no longer shows an address, a business type or a phone")
    public void yaNoMuestraLosOpcionales() {
        Map<String, String> guardados = ServiciosDePrueba.negocioGuardado(ServiciosDePrueba.negocioDelDueno());
        assertThat(guardados.get("address")).isNull();
        assertThat(guardados.get("businessType")).isNull();
        assertThat(guardados.get("phone")).isNull();
    }

    @Then("the other businesses keep their information")
    public void elOtroNegocioNoCambia() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        String otroNegocio = dueno.recall(MemoriaDeServicios.OTRO_NEGOCIO);
        Map<String, String> antes = dueno.recall(MemoriaDeServicios.OTRO_NEGOCIO_ANTES);
        assertThat(ServiciosDePrueba.negocioGuardado(otroNegocio)).isEqualTo(antes);
    }

    @And("the business type of the business is {string}")
    public void elTipoEs(String tipo) {
        assertThat(ServiciosDePrueba.negocioGuardado(ServiciosDePrueba.negocioDelDueno()).get("businessType")).isEqualTo(tipo);
    }

    @Then("the change is rejected and the business keeps its previous information")
    public void elCambioEsRechazado() {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)));
        Map<String, String> antes = dueno.recall(MemoriaDeServicios.NEGOCIO_ANTES);
        assertThat(ServiciosDePrueba.negocioGuardado(ServiciosDePrueba.negocioDelDueno())).isEqualTo(antes);
    }

    @And("they receive a message saying the name of the business is required")
    public void mensajeDeNombreObligatorio() {
        ServiciosDePrueba.dueno().should(seeThat(RespuestaDeLaApi.mensajeDelCampo("name"),
                equalTo("El nombre del negocio es obligatorio")));
    }

    @And("they receive a message saying the phone is not valid")
    public void mensajeDeTelefonoInvalido() {
        ServiciosDePrueba.dueno().should(seeThat(RespuestaDeLaApi.mensajeDelCampo("phone"),
                containsString("El teléfono no es válido")));
    }

    @And("they receive a message saying the business type is not valid")
    public void mensajeDeTipoInvalido() {
        ServiciosDePrueba.dueno().should(seeThat(RespuestaDeLaApi.mensajeDelCampo("businessType"),
                containsString("El tipo de negocio no es válido")));
    }

    @And("that business does not change")
    public void elNegocioAjenoNoCambia() {
        Map<String, String> antes = ServiciosDePrueba.dueno().recall(MemoriaDeServicios.NEGOCIO_ANTES);
        assertThat(ServiciosDePrueba.negocioGuardado(ServiciosDePrueba.negocioDelDueno())).isEqualTo(antes);
    }

    @Then("it is recorded who made the change and when")
    public void quedaRegistradoQuienYCuando() {
        Actor dueno = ServiciosDePrueba.dueno();
        DatosDeRegistro datos = dueno.recall(Memoria.DATOS);
        String negocio = dueno.recall(Memoria.ID_DE_NEGOCIO);
        assertThat(auditLogRepository.findAll())
                .as("registro de auditoría de la edición del negocio")
                .anySatisfy(registro -> {
                    assertThat(registro.getEventType()).isEqualTo(AuditEventType.EDICION_NEGOCIO);
                    assertThat(registro.getSubjectEmail()).isEqualTo(datos.correo());
                    assertThat(registro.getDetail()).contains(negocio);
                    assertThat(registro.getCreatedAt()).isNotNull();
                });
    }

    // ---------- Apoyo ----------

    /** Los cinco datos del negocio con valores válidos; el nombre no se repite entre corridas. */
    private static Map<String, Object> todosLosDatos() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("name", "Barberia Central " + UUID.randomUUID().toString().substring(0, 6));
        datos.put("description", "Cortes clasicos y barba");
        datos.put("address", "Calle 10 # 43-12, Medellin");
        datos.put("businessType", "BELLEZA");
        datos.put("phone", "6045551234");
        return datos;
    }

    /** Un texto vacío borra el dato; el tipo se guarda en mayúsculas. */
    private static String comoQuedaGuardado(String campo, Object valor) {
        String texto = String.valueOf(valor).trim();
        if (texto.isEmpty()) {
            return null;
        }
        return "businessType".equals(campo) ? texto.toUpperCase() : texto;
    }

    /** En el escenario de varios negocios, se guarda cómo está el otro negocio antes de editar el primero. */
    private void guardarElOtroNegocioSiExiste() {
        Actor dueno = ServiciosDePrueba.dueno();
        String otroNegocio = dueno.recall(MemoriaDeServicios.OTRO_NEGOCIO);
        if (otroNegocio != null) {
            dueno.remember(MemoriaDeServicios.OTRO_NEGOCIO_ANTES, ServiciosDePrueba.negocioGuardado(otroNegocio));
        }
    }

    /** Guarda lo enviado (para comprobarlo después) y el dueño envía la edición. */
    private void editar(Map<String, Object> cambios) {
        Actor dueno = ServiciosDePrueba.dueno();
        dueno.remember(MemoriaDeServicios.DATOS_ESPERADOS, cambios);
        ServiciosDePrueba.enElFoco(dueno).attemptsTo(EditarNegocio.conLosDatos(ServiciosDePrueba.negocioDelDueno(), cambios));
    }
}
