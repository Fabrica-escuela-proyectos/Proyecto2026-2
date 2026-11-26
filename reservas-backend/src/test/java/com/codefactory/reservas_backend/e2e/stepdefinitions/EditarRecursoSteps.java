package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.DatosDeRecurso;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeRecursos;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarRecursos;
import com.codefactory.reservas_backend.e2e.tasks.EditarRecurso;
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

import java.util.Map;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.RecursosComunesSteps.elegirRecurso;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.RecursosComunesSteps.enLaBase;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.RecursosComunesSteps.idDelRecurso;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.BORRADOR;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.ID_DEL_NEGOCIO_AJENO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.ID_DEL_NEGOCIO_DEL_RECURSO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.ID_DEL_RECURSO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.ID_DE_OTRO_RECURSO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.NOMBRE_DEL_RECURSO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.TIPO_DEL_RECURSO;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * HU-15 - Editar recurso. Actores: el proveedor dueño y otro proveedor. "access is denied" está en ComunesSteps y los
 * mensajes de nombre obligatorio y nombre repetido en RecursosComunesSteps. El recurso que se edita es el del
 * contexto ("el recurso"); sus datos de antes se guardan para comprobar que no cambiaron.
 */
public class EditarRecursoSteps {

    private static final String RECURSO_INEXISTENTE = "El recurso solicitado no existe";

    @Autowired
    private ResourceRepository recursos;

    // ---------- Given ----------

    @Given("an authenticated Provider has a resource registered in their business")
    @Given("an authenticated Provider is editing a resource of their business")
    public void proveedorConUnRecurso() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(proveedor, "Proveedor");
        String id = RecursosDePrueba.crear(proveedor, "Sala 1", "SALA");
        elegirRecurso(enLaBase(recursos, id));
    }

    @Given("the business has the resources {string} and {string}")
    public void elNegocioTieneLosRecursos(String primero, String segundo) {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(proveedor, "Proveedor");
        RecursosDePrueba.crear(proveedor, primero, "SALA");
        RecursosDePrueba.crear(proveedor, segundo, "SALA");
    }

    @Given("a resource belongs to the business of another Provider")
    public void unRecursoDeOtroProveedor() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(PROVEEDOR), "Proveedor");
        Actor otro = theActorCalled(OTRO_PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(otro, "Proveedor");
        String id = RecursosDePrueba.crear(otro, "Sala ajena", "SALA");
        elegirRecurso(enLaBase(recursos, id));
    }

    /** Además del recurso inexistente, el proveedor tiene uno real, para comprobar que no se toca. */
    @Given("an authenticated Provider requests to edit a resource that does not exist")
    public void proveedorSolicitaEditarUnRecursoInexistente() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(proveedor, "Proveedor");
        String existente = RecursosDePrueba.crear(proveedor, "Sala 1", "SALA");
        elegirRecurso(enLaBase(recursos, existente));
        contexto().remember(ID_DE_OTRO_RECURSO, existente);
        // El recurso del escenario pasa a ser uno que no existe.
        contexto().remember(ID_DEL_RECURSO, UUID.randomUUID().toString());
    }

    // ---------- When ----------

    @When("they modify the name or the type with valid data")
    public void modificaElNombreOElTipo() {
        contexto().remember(BORRADOR, DatosDeRecurso.de("Sala Principal", "EQUIPO"));
    }

    @When("they confirm the changes")
    public void confirmaLosCambios() {
        DatosDeRecurso cambios = contexto().recall(BORRADOR);
        editar(cambios);
    }

    @When("they remove the name and confirm the changes")
    public void eliminaElNombreYConfirma() {
        editar(DatosDeRecurso.de("", null));
    }

    @When("the Provider changes the name of {string} to {string}")
    public void cambiaElNombreDe(String actual, String nuevo) {
        String negocio = theActorCalled(PROVEEDOR).recall(Memoria.ID_DE_NEGOCIO);
        Resource objetivo = recursos.findByBusinessIdOrderByCreatedAtAsc(UUID.fromString(negocio)).stream()
                .filter(r -> r.getName().equals(actual)).findFirst().orElseThrow();
        elegirRecurso(objetivo);
        editar(DatosDeRecurso.de(nuevo, null));
    }

    @When("the Provider tries to edit it")
    public void intentaEditarlo() {
        editar(DatosDeRecurso.de("Nombre cambiado", null));
    }

    @When("they try to save the changes")
    public void intentaGuardarLosCambios() {
        editar(DatosDeRecurso.de("Sala renombrada", null));
    }

    /** El negocio de otro proveedor viaja en el cuerpo; el recurso debe seguir en el suyo. */
    @When("they try to assign it to another business")
    public void intentaAsignarloAOtroNegocio() {
        Actor otro = theActorCalled(OTRO_PROVEEDOR);
        CuentasDePrueba.registrar(otro, "Proveedor");
        String negocioAjeno = otro.recall(Memoria.ID_DE_NEGOCIO);
        contexto().remember(ID_DEL_NEGOCIO_AJENO, negocioAjeno);
        editar(DatosDeRecurso.de(null, null).conNegocioEnElCuerpo(negocioAjeno));
    }

    // ---------- Then / And ----------

    @Then("the resource is updated")
    public void elRecursoQuedaActualizado() {
        DatosDeRecurso cambios = contexto().recall(BORRADOR);
        String id = idDelRecurso();
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeRecursos.nombre(), equalTo(cambios.nombre())),
                seeThat(RespuestaDeRecursos.tipo(), equalTo(cambios.tipo())));
        Resource enLaBase = enLaBase(recursos, id);
        assertThat(enLaBase.getName()).isEqualTo(cambios.nombre());
        assertThat(enLaBase.getType().name()).isEqualTo(cambios.tipo());
    }

    @And("the changes are shown in later queries")
    public void losCambiosSeMuestranEnConsultasPosteriores() {
        DatosDeRecurso cambios = contexto().recall(BORRADOR);
        String id = idDelRecurso();
        Actor proveedor = theActorCalled(PROVEEDOR);
        String negocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        proveedor.attemptsTo(ConsultarRecursos.delNegocio(negocio));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        Map<String, Object> mostrado = RespuestaDeRecursos.recursosDeLaLista().answeredBy(proveedor).stream()
                .filter(r -> id.equals(r.get("id"))).findFirst().orElseThrow();
        assertThat(mostrado.get("name")).isEqualTo(cambios.nombre());
        assertThat(mostrado.get("type")).isEqualTo(cambios.tipo());
    }

    /** Nada se guardó: el recurso conserva el nombre y el tipo que tenía antes de la edición. */
    @Then("the changes are not saved")
    @And("the resource remains unchanged")
    public void losCambiosNoSeGuardan() {
        String nombreAntes = contexto().recall(NOMBRE_DEL_RECURSO);
        String tipoAntes = contexto().recall(TIPO_DEL_RECURSO);
        Resource actual = enLaBase(recursos, idDelRecurso());
        assertThat(actual.getName()).isEqualTo(nombreAntes);
        assertThat(actual.getType().name()).isEqualTo(tipoAntes);
    }

    @Then("no modification is made")
    public void noSeRealizaNingunaModificacion() {
        String existente = contexto().recall(ID_DE_OTRO_RECURSO);
        String nombreAntes = contexto().recall(NOMBRE_DEL_RECURSO);
        String tipoAntes = contexto().recall(TIPO_DEL_RECURSO);
        Resource real = enLaBase(recursos, existente);
        assertThat(real.getName()).isEqualTo(nombreAntes);
        assertThat(real.getType().name()).isEqualTo(tipoAntes);
        assertThat(recursos.findById(UUID.fromString(idDelRecurso()))).isEmpty();
    }

    @And("it is reported that the resource does not exist")
    public void seInformaQueElRecursoNoExiste() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(404)),
                seeThat(RespuestaDeLaApi.mensaje(), equalTo(RECURSO_INEXISTENTE)));
    }

    @Then("the change of business is ignored")
    public void elCambioDeNegocioSeIgnora() {
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
    }

    @And("the resource remains associated with its original business")
    public void sigueAsociadoASuNegocioOriginal() {
        String original = contexto().recall(ID_DEL_NEGOCIO_DEL_RECURSO);
        String ajeno = contexto().recall(ID_DEL_NEGOCIO_AJENO);
        Actor proveedor = theActorCalled(PROVEEDOR);
        assertThat(RespuestaDeRecursos.idDelNegocio().answeredBy(proveedor)).isEqualTo(original);
        assertThat(enLaBase(recursos, idDelRecurso()).getBusinessId().toString()).isEqualTo(original);
        assertThat(recursos.findByBusinessIdOrderByCreatedAtAsc(UUID.fromString(ajeno))).isEmpty();
    }

    // ---------- Apoyo ----------

    /** El proveedor edita "el recurso" del escenario; el actor en foco queda siendo él (lo exige "access is denied"). */
    private void editar(DatosDeRecurso cambios) {
        String id = idDelRecurso();
        contexto().remember(BORRADOR, cambios);
        theActorCalled(PROVEEDOR).attemptsTo(EditarRecurso.con(id, cambios));
    }
}
