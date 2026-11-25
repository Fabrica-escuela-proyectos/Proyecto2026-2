package com.codefactory.reservas_backend.e2e.questions;

import io.restassured.path.json.JsonPath;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

/** Preguntas sobre el servicio que devolvió la API en la última respuesta. */
public final class ElServicio {

    private ElServicio() {
    }

    private static JsonPath cuerpo(Actor actor) {
        return LastResponse.received().answeredBy(actor).jsonPath();
    }

    public static Question<String> idDelNegocio() {
        return Question.about("el negocio al que pertenece el servicio")
                .answeredBy(actor -> cuerpo(actor).getString("businessId"));
    }

    public static Question<Boolean> estaActivo() {
        return Question.about("si el servicio está activo")
                .answeredBy(actor -> cuerpo(actor).getBoolean("active"));
    }
}
