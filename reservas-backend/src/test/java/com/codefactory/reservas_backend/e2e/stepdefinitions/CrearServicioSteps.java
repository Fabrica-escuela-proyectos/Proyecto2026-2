package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.questions.ElServicio;
import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import com.codefactory.reservas_backend.e2e.tasks.CrearServicio;
import com.codefactory.reservas_backend.e2e.user.CuentasDePrueba;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;

import static com.codefactory.reservas_backend.e2e.user.Personajes.PROVEEDOR_EXISTENTE;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorCalled;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/** HU-09 - Crear servicio. */
public class CrearServicioSteps {

    // ---------- Given ----------

    @Given("a provider is logged in and owns a business")
    public void proveedorConSesionYNegocio() {
        CuentasDePrueba.registrarEIniciarSesion(theActorCalled(PROVEEDOR_EXISTENTE), "Proveedor");
    }

    // ---------- When ----------

    @When("they register a service with a valid name, duration and price")
    public void registraUnServicioValido() {
        Actor proveedor = theActorCalled(PROVEEDOR_EXISTENTE);
        String idDelNegocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        proveedor.attemptsTo(CrearServicio.enElNegocio(idDelNegocio, "Consulta general", 30, 50_000));
    }

    // ---------- Then / And ----------

    @Then("the service is created in their business")
    public void elServicioQuedaCreado() {
        Actor proveedor = theActorCalled(PROVEEDOR_EXISTENTE);
        String idDelNegocio = proveedor.recall(Memoria.ID_DE_NEGOCIO);
        proveedor.should(
                seeThat(RespuestaDeLaApi.codigoDeEstado(), equalTo(201)),
                seeThat(ElServicio.idDelNegocio(), equalTo(idDelNegocio)));
    }

    @And("the service is active")
    public void elServicioEstaActivo() {
        theActorCalled(PROVEEDOR_EXISTENTE).should(seeThat(ElServicio.estaActivo(), is(true)));
    }
}