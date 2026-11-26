package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeRecursos;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReservas;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarDisponibilidad;
import com.codefactory.reservas_backend.e2e.tasks.ReactivarRecurso;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.RecursosDePrueba;
import com.codefactory.reservas_backend.resource.domain.Resource;
import com.codefactory.reservas_backend.resource.infrastructure.ResourceRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.RecursosComunesSteps.elegirRecurso;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.RecursosComunesSteps.enLaBase;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.RecursosComunesSteps.idDelRecurso;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.RecursosComunesSteps.prepararRecursoDeOtroNegocio;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.agenda;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.CANTIDAD_ANTES;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.ID_DEL_NEGOCIO_DEL_RECURSO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeRecursos.NOMBRE_DEL_RECURSO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * HU-17 - Reactivar recurso. Actores: el proveedor dueño, otro proveedor (dueño del recurso ajeno) y un cliente sin
 * sesión que consulta la disponibilidad (es pública). "an authenticated Provider", "the operation is not allowed" y
 * "the resource keeps its current status" están en RecursosComunesSteps. El recurso se deja desactivado por la API
 * (HU-16) antes de reactivarlo.
 */
public class ReactivarRecursoSteps {

    @Autowired
    private ResourceRepository recursos;

    // ---------- Given ----------

    /** Desactiva "el recurso" del proveedor (sin reservas futuras no hace falta confirmar). */
    @Given("the resource is currently deactivated")
    @Given("an existing resource of their own business is currently deactivated")
    public void elRecursoEstaDesactivado() {
        String id = idDelRecurso();
        RecursosDePrueba.desactivar(theActorCalled(PROVEEDOR), id);
        elegirRecurso(enLaBase(recursos, id));
    }

    @Given("a deactivated resource that belongs to another business")
    public void unRecursoDesactivadoDeOtroNegocio() {
        prepararRecursoDeOtroNegocio(true);
    }

    // ---------- When ----------

    @When("they request to reactivate the resource")
    public void solicitaReactivarElRecurso() {
        String id = idDelRecurso();
        String negocio = contexto().recall(ID_DEL_NEGOCIO_DEL_RECURSO);
        contexto().remember(CANTIDAD_ANTES, recursos.findByBusinessIdOrderByCreatedAtAsc(UUID.fromString(negocio)).size());
        theActorCalled(PROVEEDOR).attemptsTo(ReactivarRecurso.conId(id));
    }

    // ---------- Then / And ----------

    @Then("the resource becomes active")
    public void elRecursoQuedaActivo() {
        String id = idDelRecurso();
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeRecursos.activo(), is(true)));
        assertThat(enLaBase(recursos, id).isActive()).isTrue();
    }

    /** Es el mismo recurso (mismo id y mismo nombre) y el negocio no tiene ninguno más que antes. */
    @Then("the existing resource is reactivated")
    public void elRecursoExistenteQuedaReactivado() {
        String id = idDelRecurso();
        String nombre = contexto().recall(NOMBRE_DEL_RECURSO);
        String negocio = contexto().recall(ID_DEL_NEGOCIO_DEL_RECURSO);
        Integer antes = contexto().recall(CANTIDAD_ANTES);
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeRecursos.id(), equalTo(id)),
                seeThat(RespuestaDeRecursos.nombre(), equalTo(nombre)),
                seeThat(RespuestaDeRecursos.activo(), is(true)));
        Resource reactivado = enLaBase(recursos, id);
        assertThat(reactivado.isActive()).isTrue();
        assertThat(recursos.findByBusinessIdOrderByCreatedAtAsc(UUID.fromString(negocio))).hasSize(antes);
    }

    /** Con el recurso activo otra vez, el servicio vuelve a mostrar horarios libres. */
    @And("it is available again to receive bookings")
    public void vuelveAEstarDisponible() {
        Agenda agenda = agenda();
        Actor cliente = theActorCalled(CLIENTE);
        cliente.attemptsTo(ConsultarDisponibilidad.delServicio(agenda.idDelServicio(),
                AgendaDePrueba.hoy().plusDays(7).toString()));
        cliente.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        assertThat(RespuestaDeReservas.horariosLibres().answeredBy(cliente)).isNotEmpty();
    }
}
