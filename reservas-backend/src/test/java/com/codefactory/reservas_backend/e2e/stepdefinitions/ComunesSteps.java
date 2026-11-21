package com.codefactory.reservas_backend.e2e.stepdefinitions;

import com.codefactory.reservas_backend.e2e.questions.RespuestaDeLaApi;
import io.cucumber.java.en.Then;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.actors.OnStage.theActorInTheSpotlight;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;

/** Pasos que comparten varias historias (HU-04, HU-05 y HU-06). Cada texto se define una sola vez. */
public class ComunesSteps {

    /** Sin sesión válida la API responde 401; con sesión pero sin permiso, 403. */
    @Then("se le deniega el acceso")
    public void seLeDeniegaElAcceso() {
        theActorInTheSpotlight().should(seeThat(RespuestaDeLaApi.codigoDeEstado(), anyOf(equalTo(401), equalTo(403))));
    }
}
