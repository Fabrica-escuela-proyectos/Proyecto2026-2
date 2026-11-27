package com.codefactory.reservas_backend.e2e.stepdefinitions.us18;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeHorariosYRecursos;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.AsignarRecursos;
import com.codefactory.reservas_backend.e2e.tasks.CrearServicio;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarRecurso;
import com.codefactory.reservas_backend.e2e.tasks.us18.ConsultarRecursosDelServicio;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

import java.util.List;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.user.MemoriaDeHorarios.ID_DEL_SERVICIO_PROPIO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeHorarios.RECURSOS_PROPIOS;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.AGENDA;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CONTEXTO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/** US 18: asignar recursos a un servicio. */
public class AsignarRecursosSteps {

    @Given("a Provider has a service and two resources in their business")
    public void proveedorConServicioYDosRecursos() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(proveedor, "Proveedor");
        String idDelNegocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);

        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        proveedor.attemptsTo(CrearServicio.enElNegocio(idDelNegocio, "Servicio " + sufijo,
                AgendaDePrueba.DURACION, AgendaDePrueba.PRECIO_POR_DEFECTO));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        String idDelServicio = ultimoId(proveedor);

        proveedor.attemptsTo(RegistrarRecurso.llamado("Sala A " + sufijo, idDelNegocio));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        String primerRecurso = ultimoId(proveedor);

        proveedor.attemptsTo(RegistrarRecurso.llamado("Sala B " + sufijo, idDelNegocio));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        String segundoRecurso = ultimoId(proveedor);

        Actor contexto = theActorCalled(CONTEXTO);
        contexto.remember(ID_DEL_SERVICIO_PROPIO, idDelServicio);
        contexto.remember(RECURSOS_PROPIOS, List.of(primerRecurso, segundoRecurso));
    }

    @Given("a Provider has a service with one resource assigned")
    public void proveedorConServicioYUnRecursoAsignado() {
        Agenda agenda = AgendaDePrueba.prepararNegocio(theActorCalled(PROVEEDOR));
        theActorCalled(CONTEXTO).remember(AGENDA, agenda);
    }

    @When("they assign both resources to the service")
    public void asignaAmbosRecursosAlServicio() {
        Actor contexto = theActorCalled(CONTEXTO);
        String idDelServicio = contexto.recall(ID_DEL_SERVICIO_PROPIO);
        List<String> recursos = contexto.recall(RECURSOS_PROPIOS);
        theActorCalled(PROVEEDOR).attemptsTo(AsignarRecursos.alServicio(idDelServicio, recursos));
    }

    @When("they try to assign a resource that belongs to another business")
    public void intentaAsignarRecursoDeOtroNegocio() {
        Agenda agenda = theActorCalled(CONTEXTO).recall(AGENDA);
        Agenda ajena = AgendaDePrueba.prepararNegocio(theActorCalled(OTRO_PROVEEDOR));
        theActorCalled(PROVEEDOR).attemptsTo(
                AsignarRecursos.alServicio(agenda.idDelServicio(), List.of(agenda.idDelRecurso(), ajena.idDelRecurso())));
    }

    @When("they try to assign a resource that does not exist")
    public void intentaAsignarRecursoInexistente() {
        Agenda agenda = theActorCalled(CONTEXTO).recall(AGENDA);
        String idInexistente = UUID.randomUUID().toString();
        theActorCalled(PROVEEDOR).attemptsTo(
                AsignarRecursos.alServicio(agenda.idDelServicio(), List.of(agenda.idDelRecurso(), idInexistente)));
    }

    @Then("the service is offered on both resources")
    public void elServicioQuedaOfrecidoEnAmbosRecursos() {
        Actor contexto = theActorCalled(CONTEXTO);
        String idDelServicio = contexto.recall(ID_DEL_SERVICIO_PROPIO);
        List<String> esperados = contexto.recall(RECURSOS_PROPIOS);

        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        proveedor.attemptsTo(ConsultarRecursosDelServicio.delServicio(idDelServicio));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));

        List<String> asignados = RespuestaDeHorariosYRecursos.idsDeRecursosAsignados().answeredBy(proveedor);
        assertThat(asignados).containsExactlyInAnyOrderElementsOf(esperados);
    }

    @Then("the assignment is rejected")
    public void laAsignacionEsRechazada() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)),
                seeThat(RespuestaDeLaApi.mensaje(), containsString("no existen o no pertenecen al negocio")));
    }

    @And("the service keeps only its previous resource")
    public void elServicioConservaSoloSuRecursoAnterior() {
        Agenda agenda = theActorCalled(CONTEXTO).recall(AGENDA);
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.attemptsTo(ConsultarRecursosDelServicio.delServicio(agenda.idDelServicio()));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));

        List<String> asignados = RespuestaDeHorariosYRecursos.idsDeRecursosAsignados().answeredBy(proveedor);
        assertThat(asignados).containsExactly(agenda.idDelRecurso());
    }

    private static String ultimoId(Actor actor) {
        return LastResponse.received().answeredBy(actor).jsonPath().getString("id");
    }
}
