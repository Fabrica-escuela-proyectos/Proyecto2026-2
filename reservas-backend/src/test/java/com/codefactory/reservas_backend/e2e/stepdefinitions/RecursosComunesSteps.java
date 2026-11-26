package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.RecursosDePrueba;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarAgenda;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.ACTIVO_ANTES;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.ID_DEL_NEGOCIO_DEL_RECURSO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.ID_DEL_RECURSO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.NOMBRE_DEL_RECURSO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.TIPO_DEL_RECURSO;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Pasos que comparten las HU de recursos (HU-14 a HU-17). Cada texto se define una sola vez.
 * El recurso sobre el que actúa el escenario ("el recurso") vive en la memoria del actor CONTEXTO.
 * Ojo: contexto() pone a ese actor en el foco; por eso los pasos de acción leen primero el contexto y
 * actúan al final, para que "access is denied" (que usa el actor en foco) mire al actor correcto.
 */
public class RecursosComunesSteps {

    static final String NOMBRE_OBLIGATORIO = "El nombre del recurso es obligatorio";
    static final String NOMBRE_DUPLICADO = "Ya existe un recurso con ese nombre en el negocio";

    @Autowired
    private ResourceRepository recursos;

    // ---------- Given ----------

    /** Proveedor con sesión y un negocio listo (servicio, recurso asignado y horario). "El recurso" es el de la agenda. */
    @Given("an authenticated Provider")
    @Given("an authenticated Provider manages a resource of their own business")
    public void proveedorConUnRecursoPropio() {
        prepararProveedorConRecurso();
    }

    // ---------- Then / And ----------

    @Then("the operation is not allowed")
    public void laOperacionNoEsPermitida() {
        theActorCalled(PROVEEDOR).should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(403)));
    }

    /** El recurso queda como estaba antes de la acción (activo o inactivo, según cómo se preparó). */
    @And("the resource keeps its current status")
    public void elRecursoConservaSuEstado() {
        Boolean activoAntes = contexto().recall(ACTIVO_ANTES);
        assertThat(enLaBase(recursos, idDelRecurso()).isActive()).isEqualTo(activoAntes);
    }

    /** HU-14 (registrar) y HU-15 (editar): el nombre vacío se rechaza con 400 y un mensaje del campo "name". */
    @And("the Provider is informed that the name is required")
    public void seInformaQueElNombreEsObligatorio() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)),
                seeThat(RespuestaDeLaApi.mensajeDelCampo("name"), equalTo(NOMBRE_OBLIGATORIO)));
    }

    /** HU-14 y HU-15: nombre repetido dentro del mismo negocio, 409. */
    @And("the Provider is informed that a resource with that name already exists")
    public void seInformaQueElNombreYaExiste() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(409)),
                seeThat(RespuestaDeLaApi.mensaje(), equalTo(NOMBRE_DUPLICADO)));
    }

    // ---------- Apoyo (también lo usan las demás clases de pasos de recursos) ----------

    /** Proveedor con su negocio listo; deja la agenda en el contexto y su recurso como el recurso del escenario. */
    static Agenda prepararProveedorConRecurso() {
        Agenda agenda = AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR));
        guardarAgenda(agenda);
        elegirRecurso(agenda.idDelRecurso(), agenda.idDelNegocio(), agenda.nombreDelRecurso(), "SALA", true);
        return agenda;
    }

    /** Otro proveedor con su negocio y un recurso, que pasa a ser el recurso del escenario (HU-16 y HU-17). */
    static void prepararRecursoDeOtroNegocio(boolean desactivado) {
        Actor otro = theActorCalled(OTRO_PROVEEDOR);
        Agenda ajena = AgendaDePrueba.prepararNegocio(otro);
        if (desactivado) {
            RecursosDePrueba.desactivar(otro, ajena.idDelRecurso());
        }
        elegirRecurso(ajena.idDelRecurso(), ajena.idDelNegocio(), ajena.nombreDelRecurso(), "SALA", !desactivado);
    }

    static void elegirRecurso(String id, String idDelNegocio, String nombre, String tipo, boolean activo) {
        Actor contexto = contexto();
        contexto.remember(ID_DEL_RECURSO, id);
        contexto.remember(ID_DEL_NEGOCIO_DEL_RECURSO, idDelNegocio);
        contexto.remember(NOMBRE_DEL_RECURSO, nombre);
        contexto.remember(TIPO_DEL_RECURSO, tipo);
        contexto.remember(ACTIVO_ANTES, activo);
    }

    static void elegirRecurso(Resource recurso) {
        elegirRecurso(recurso.getId().toString(), recurso.getBusinessId().toString(), recurso.getName(),
                recurso.getType().name(), recurso.isActive());
    }

    static String idDelRecurso() {
        return contexto().recall(ID_DEL_RECURSO);
    }

    static Resource enLaBase(ResourceRepository recursos, String id) {
        return recursos.findById(UUID.fromString(id)).orElseThrow(
                () -> new AssertionError("El recurso " + id + " ya no existe en la base"));
    }
}
