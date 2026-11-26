package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.DatosDeRecurso;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeRecursos;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarRecursoConDatos;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import com.codefactory.reservas_backend.e2e.user.RecursosDePrueba;
import com.codefactory.reservas_backend.resource.domain.Resource;
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
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.BORRADOR;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.CANTIDAD_ANTES;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.ID_DEL_NEGOCIO_AJENO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.ID_DEL_NEGOCIO_DEL_RECURSO;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.Personajes.USUARIO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * HU-14 - Registrar recurso. Actores: el proveedor dueño, otro proveedor y un usuario con rol Cliente.
 * "the Provider is informed that the name is required" y "... a resource with that name already exists" están en
 * RecursosComunesSteps (los comparte HU-15); "access is denied" en ComunesSteps.
 * Que el recurso "no queda registrado" se comprueba en la base (así no se pisa la respuesta que leen los pasos siguientes).
 */
public class RegistrarRecursoSteps {

    private static final String TIPO_OBLIGATORIO = "El tipo del recurso es obligatorio";

    @Autowired
    private ResourceRepository recursos;

    // ---------- Given ----------

    @Given("an authenticated Provider wants to register a resource")
    public void proveedorQuiereRegistrarUnRecurso() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(PROVEEDOR), "Proveedor");
    }

    @Given("the business of the Provider already has a resource named {string}")
    public void elNegocioYaTieneUnRecurso(String nombre) {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(proveedor, "Proveedor");
        RecursosDePrueba.crear(proveedor, nombre, "SALA");
    }

    @Given("a user with the Client role is authenticated")
    public void usuarioConRolCliente() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(USUARIO), "Cliente");
    }

    // ---------- When ----------

    @When("they provide the name and the type of the resource")
    public void proporcionaElNombreYElTipo() {
        contexto().remember(BORRADOR,
                DatosDeRecurso.de("Sala " + UUID.randomUUID().toString().substring(0, 6), "SALA"));
    }

    @When("they confirm the registration")
    public void confirmaElRegistro() {
        DatosDeRecurso datos = contexto().recall(BORRADOR);
        intentarRegistrar(datos);
    }

    @When("they try to register it without providing a name")
    public void intentaRegistrarSinNombre() {
        intentarRegistrar(DatosDeRecurso.de(null, "SALA"));
    }

    @When("they try to register it without indicating the type")
    public void intentaRegistrarSinTipo() {
        intentarRegistrar(DatosDeRecurso.de("Sala sin tipo", null));
    }

    @When("they try to register another resource with the name {string}")
    public void intentaRegistrarOtroConElMismoNombre(String nombre) {
        intentarRegistrar(DatosDeRecurso.de(nombre, "SALA"));
    }

    /** El negocio de otro proveedor viaja en el cuerpo; el que manda es el de la ruta (el del proveedor autenticado). */
    @When("they try to associate it with a business that is not theirs")
    public void intentaAsociarloAOtroNegocio() {
        Actor otro = theActorCalled(OTRO_PROVEEDOR);
        CuentasDePrueba.registrar(otro, "Proveedor");
        String negocioAjeno = otro.recall(Memoria.ID_DE_NEGOCIO);
        contexto().remember(ID_DEL_NEGOCIO_AJENO, negocioAjeno);
        intentarRegistrar(DatosDeRecurso.de("Sala " + UUID.randomUUID().toString().substring(0, 6), "SALA")
                .conNegocioEnElCuerpo(negocioAjeno));
    }

    /** El negocio existe y tiene dueño; quien intenta registrar es el usuario con rol Cliente. */
    @When("they try to register a resource")
    public void intentaRegistrarUnRecurso() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrar(proveedor, "Proveedor");
        String negocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        contexto().remember(ID_DEL_NEGOCIO_DEL_RECURSO, negocio);
        theActorCalled(USUARIO).attemptsTo(
                RegistrarRecursoConDatos.en(negocio, DatosDeRecurso.de("Sala 1", "SALA")));
    }

    // ---------- Then / And ----------

    @Then("the resource is registered")
    public void elRecursoQuedaRegistrado() {
        DatosDeRecurso datos = contexto().recall(BORRADOR);
        Actor proveedor = theActorCalled(PROVEEDOR);
        String negocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        proveedor.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)),
                seeThat(RespuestaDeRecursos.nombre(), equalTo(datos.nombre())),
                seeThat(RespuestaDeRecursos.tipo(), equalTo(datos.tipo())));
        assertThat(recursos.findByBusinessIdOrderByCreatedAtAsc(UUID.fromString(negocio)))
                .extracting(Resource::getName).containsExactly(datos.nombre());
    }

    /** El recurso es del negocio del proveedor que lo creó y de ningún otro. */
    @And("it is associated only with the business of the Provider who created it")
    @And("the resource is associated only with their own business")
    public void quedaAsociadoSoloASuNegocio() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        String negocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        Resource creado = enLaBase(recursos, RespuestaDeRecursos.id().answeredBy(proveedor));
        assertThat(RespuestaDeRecursos.idDelNegocio().answeredBy(proveedor)).isEqualTo(negocio);
        assertThat(creado.getBusinessId().toString()).isEqualTo(negocio);
        List<Resource> conEseNombreEnOtrosNegocios = recursos.findAll().stream()
                .filter(r -> r.getName().equals(creado.getName()) && !r.getBusinessId().equals(creado.getBusinessId()))
                .toList();
        assertThat(conEseNombreEnOtrosNegocios).isEmpty();
    }

    @Then("the resource is not registered")
    public void elRecursoNoQuedaRegistrado() {
        Integer antes = contexto().recall(CANTIDAD_ANTES);
        String negocio = theActorCalled(PROVEEDOR).recall(Memoria.ID_DE_NEGOCIO);
        assertThat(recursos.findByBusinessIdOrderByCreatedAtAsc(UUID.fromString(negocio))).hasSize(antes);
    }

    @And("the Provider is informed that the type is required")
    public void seInformaQueElTipoEsObligatorio() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)),
                seeThat(RespuestaDeLaApi.mensajeDelCampo("type"), equalTo(TIPO_OBLIGATORIO)));
    }

    @Then("the indicated business is ignored")
    public void elNegocioIndicadoSeIgnora() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        String propio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        String ajeno = contexto().recall(ID_DEL_NEGOCIO_AJENO);
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        String asociado = RespuestaDeRecursos.idDelNegocio().answeredBy(proveedor);
        assertThat(asociado).isEqualTo(propio).isNotEqualTo(ajeno);
    }

    @And("no resource is created")
    public void noSeCreaNingunRecurso() {
        String negocio = contexto().recall(ID_DEL_NEGOCIO_DEL_RECURSO);
        assertThat(recursos.findByBusinessIdOrderByCreatedAtAsc(UUID.fromString(negocio))).isEmpty();
    }

    // ---------- Apoyo ----------

    /** Anota cuántos recursos tenía el negocio y envía el registro del proveedor; el actor en foco queda siendo él. */
    private void intentarRegistrar(DatosDeRecurso datos) {
        Actor proveedor = theActorCalled(PROVEEDOR);
        String negocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        contexto().remember(CANTIDAD_ANTES, recursos.findByBusinessIdOrderByCreatedAtAsc(UUID.fromString(negocio)).size());
        contexto().remember(BORRADOR, datos);
        proveedor.attemptsTo(RegistrarRecursoConDatos.en(negocio, datos));
    }
}
