package com.codefactory.reservas_backend.e2e.stepdefinitions;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.actors.Cast;
import net.serenitybdd.screenplay.actors.OnStage;
import net.serenitybdd.screenplay.rest.abilities.CallAnApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.function.Consumer;

public class Hooks {

    @Autowired
    private Environment environment;

    @Before
    public void prepararEscenario() {
        String urlBase = "http://localhost:" + environment.getProperty("local.server.port");
        // Un CallAnApi propio por actor: cada uno conserva su última respuesta sin pisar la de los demás.
        Consumer<Actor> conAcceso = actor -> actor.can(CallAnApi.at(urlBase));
        OnStage.setTheStage(Cast.whereEveryoneCan(conAcceso));
    }

    @After
    public void cerrarEscenario() {
        OnStage.drawTheCurtain();
    }
}
