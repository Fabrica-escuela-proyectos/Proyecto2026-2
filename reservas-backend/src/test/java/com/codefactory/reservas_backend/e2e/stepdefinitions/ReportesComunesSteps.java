package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.models.Agenda;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeReportes;
import com.codefactory.reservas_backend.e2e.user.AgendaDePrueba;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import net.serenitybdd.screenplay.Actor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.contexto;
import static com.codefactory.reservas_backend.e2e.stepdefinitions.ReservasComunesSteps.guardarAgenda;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.HORAS_POR_DIA;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReportes.HORA_DE_APERTURA;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.CLIENTE;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.DESDE;
import static com.codefactory.reservas_backend.e2e.user.MemoriaDeReservas.HASTA;
import static com.codefactory.reservas_backend.e2e.user.Personajes.OTRO_PROVEEDOR;
import static com.codefactory.reservas_backend.e2e.user.PersonajesDeReservas.PROVEEDOR;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class ReportesComunesSteps {

    @Then("the query is rejected")
    public void laConsultaEsRechazada() {
        theActorInTheSpotlight().should(seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(400)));
    }

    // formato, orden o mas de 366 dias: los tres mensajes de ReportPeriod
    @And("they receive a message saying the query period is invalid")
    public void mensajeDePeriodoInvalido() {
        theActorInTheSpotlight().should(seeThat(RespuestaDeLaApi.mensaje(), anyOf(
                containsString("no son válidas"), containsString("no es válido"), containsString("no puede superar"))));
    }

    @Then("access is denied and they do not see any data of that business")
    public void accesoDenegadoSinDatos() {
        theActorInTheSpotlight().should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(403)),
                seeThat(RespuestaDeReportes.texto("businessId"), nullValue()));
    }

    // proveedor con un recurso con el nombre y horario pedidos, y un cliente listo para las reservas
    static Agenda proveedorConRecurso(String recurso, String abre, String cierra) {
        return proveedorConServicioYRecurso("Servicio " + UUID.randomUUID().toString().substring(0, 6), recurso, abre, cierra);
    }

    static Agenda proveedorConServicioYRecurso(String servicio, String recurso, String abre, String cierra) {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrarEIniciarSesion(proveedor, "Proveedor");
        Agenda agenda = AgendaDePrueba.montarAgenda(proveedor, AgendaDePrueba.PRECIO_POR_DEFECTO, servicio, recurso,
                abre, cierra);
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(CLIENTE), "Cliente");
        int apertura = LocalTime.parse(abre).getHour();
        guardarAgenda(agenda);
        contexto().remember(HORA_DE_APERTURA, apertura);
        contexto().remember(HORAS_POR_DIA, LocalTime.parse(cierra).getHour() - apertura);
        return agenda;
    }

    static void periodo(String desde, String hasta) {
        contexto().remember(DESDE, desde);
        contexto().remember(HASTA, hasta);
    }

    static void periodo(LocalDate desde, LocalDate hasta) {
        periodo(desde.toString(), hasta.toString());
    }

    static String desde() {
        return contexto().recall(DESDE);
    }

    static String hasta() {
        return contexto().recall(HASTA);
    }

    static String negocioDeOtroProveedor() {
        Actor otro = theActorCalled(OTRO_PROVEEDOR);
        CuentasDePrueba.registrar(otro, "Proveedor");
        return otro.recall(Memoria.ID_DE_NEGOCIO);
    }

    // el negocio solo hace falta para armar la ruta; quien consulta es otro actor
    static String unNegocioCualquiera() {
        Actor proveedor = theActorCalled(PROVEEDOR);
        CuentasDePrueba.registrar(proveedor, "Proveedor");
        return proveedor.recall(Memoria.ID_DE_NEGOCIO);
    }

    static void esElPeriodoPorDefecto(Actor actor) {
        LocalDate hoy = AgendaDePrueba.hoy();
        actor.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(200)),
                seeThat(RespuestaDeReportes.texto("from"), equalTo(hoy.minusDays(29).toString())),
                seeThat(RespuestaDeReportes.texto("to"), equalTo(hoy.toString())));
    }
}
