package com.codefactory.reservas_backend.e2e.stepdefinitions.us19;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeHorariosYRecursos;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.ConsultarHorarioDelRecurso;
import com.codefactory.reservas_backend.e2e.tasks.DefinirHorario;
import com.codefactory.reservas_backend.e2e.tasks.RegistrarRecurso;
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
import java.util.Map;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.user.MemoriaDeHorarios.HORARIO_ANTERIOR;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeHorarios.ID_DEL_RECURSO_PROPIO;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.AGENDA;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CONTEXTO;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/** US 19: definir horarios de atención de un recurso. */
public class DefinirHorarioRecursoSteps {

    private static final int LUNES = 1;
    private static final int DOMINGO = 7;
    private static final String INICIO_SEMANA = "09:00";
    private static final String FIN_SEMANA = "17:00";
    private static final String NUEVO_INICIO_LUNES = "08:00";
    private static final String NUEVO_FIN_LUNES = "14:00";

    @Given("a Provider owns a resource without opening hours")
    public void proveedorConRecursoSinHorario() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(proveedor, "Proveedor");
        String idDelNegocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);

        proveedor.attemptsTo(RegistrarRecurso.llamado("Sala " + UUID.randomUUID().toString().substring(0, 6), idDelNegocio));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)));
        String idDelRecurso = LastResponse.received().answeredBy(proveedor).jsonPath().getString("id");

        theActorCalled(CONTEXTO).remember(ID_DEL_RECURSO_PROPIO, idDelRecurso);
    }

    /** También lo comparte US 21: deja el negocio con servicio, recurso asignado y horario semanal listo. */
    @Given("a Provider owns a resource with weekly opening hours")
    public void proveedorConRecursoConHorarioSemanal() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        Agenda agenda = AgendaDePrueba.prepararNegocio(proveedor);

        proveedor.attemptsTo(ConsultarHorarioDelRecurso.delRecurso(agenda.idDelRecurso()));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        Map<Integer, List<Map<String, String>>> horarioActual =
                RespuestaDeHorariosYRecursos.horarioSemanal().answeredBy(proveedor);

        Actor contexto = theActorCalled(CONTEXTO);
        contexto.remember(AGENDA, agenda);
        contexto.remember(ID_DEL_RECURSO_PROPIO, agenda.idDelRecurso());
        contexto.remember(HORARIO_ANTERIOR, horarioActual);
    }

    @When("they define opening hours from Monday to Saturday and leave Sunday empty")
    public void defineHorarioDeLunesASabado() {
        String idDelRecurso = theActorCalled(CONTEXTO).recall(ID_DEL_RECURSO_PROPIO);
        theActorCalled(PROVEEDOR).attemptsTo(DefinirHorario.deLunesASabado(idDelRecurso, INICIO_SEMANA, FIN_SEMANA));
    }

    @When("they change only the Monday hours")
    public void cambiaSoloElHorarioDelLunes() {
        String idDelRecurso = theActorCalled(CONTEXTO).recall(ID_DEL_RECURSO_PROPIO);
        theActorCalled(PROVEEDOR).attemptsTo(
                DefinirHorario.delDia(idDelRecurso, LUNES, NUEVO_INICIO_LUNES, NUEVO_FIN_LUNES));
    }

    @When("they set a range from {string} to {string} on Monday")
    public void defineRangoEnLunes(String inicio, String fin) {
        String idDelRecurso = theActorCalled(CONTEXTO).recall(ID_DEL_RECURSO_PROPIO);
        theActorCalled(PROVEEDOR).attemptsTo(DefinirHorario.delDia(idDelRecurso, LUNES, inicio, fin));
    }

    @Then("the opening hours are saved")
    public void elHorarioQuedaGuardado() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        Map<Integer, List<Map<String, String>>> horario =
                RespuestaDeHorariosYRecursos.horarioSemanal().answeredBy(proveedor);
        List<Map<String, String>> esperado = List.of(Map.of("start", INICIO_SEMANA, "end", FIN_SEMANA));
        for (int dia = LUNES; dia <= 6; dia++) {
            assertThat(horario.get(dia)).as("horario del día %d", dia).isEqualTo(esperado);
        }
    }

    @And("Sunday is marked as not available")
    public void domingoQuedaNoDisponible() {
        Map<Integer, List<Map<String, String>>> horario =
                RespuestaDeHorariosYRecursos.horarioSemanal().answeredBy(theActorCalled(PROVEEDOR));
        assertThat(horario.get(DOMINGO)).isEmpty();
    }

    @Then("the Monday hours are updated")
    public void elHorarioDelLunesSeActualiza() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));
        Map<Integer, List<Map<String, String>>> horario =
                RespuestaDeHorariosYRecursos.horarioSemanal().answeredBy(proveedor);
        assertThat(horario.get(LUNES))
                .isEqualTo(List.of(Map.of("start", NUEVO_INICIO_LUNES, "end", NUEVO_FIN_LUNES)));
    }

    @And("the other days keep their previous hours")
    public void losDemasDiasConservanSuHorarioAnterior() {
        Map<Integer, List<Map<String, String>>> anterior = theActorCalled(CONTEXTO).recall(HORARIO_ANTERIOR);
        Map<Integer, List<Map<String, String>>> actual =
                RespuestaDeHorariosYRecursos.horarioSemanal().answeredBy(theActorCalled(PROVEEDOR));
        for (int dia = 2; dia <= DOMINGO; dia++) {
            assertThat(actual.get(dia)).as("horario del día %d", dia).isEqualTo(anterior.get(dia));
        }
    }

    @Then("the change is rejected")
    public void elCambioEsRechazado() {
        theActorCalled(PROVEEDOR).should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)),
                seeThat(RespuestaDeLaApi.mensaje(), not(emptyOrNullString())));
    }

    @And("the resource keeps its previous opening hours")
    public void elRecursoConservaSuHorarioAnterior() {
        Actor contexto = theActorCalled(CONTEXTO);
        String idDelRecurso = contexto.recall(ID_DEL_RECURSO_PROPIO);
        Map<Integer, List<Map<String, String>>> anterior = contexto.recall(HORARIO_ANTERIOR);

        Actor proveedor = theActorCalled(PROVEEDOR);
        proveedor.attemptsTo(ConsultarHorarioDelRecurso.delRecurso(idDelRecurso));
        proveedor.should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)));

        Map<Integer, List<Map<String, String>>> actual =
                RespuestaDeHorariosYRecursos.horarioSemanal().answeredBy(proveedor);
        assertThat(actual).isEqualTo(anterior);
    }
}
