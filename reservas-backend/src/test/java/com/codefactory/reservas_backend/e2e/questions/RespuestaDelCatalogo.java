package com.codefactory.reservas_backend.e2e.questions;

import io.restassured.path.json.JsonPath;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

import java.util.List;
import java.util.Map;

/** Preguntas sobre la última respuesta del catálogo de negocios (HU-13). */
public final class RespuestaDelCatalogo {

    private RespuestaDelCatalogo() {
    }

    private static JsonPath json(Actor actor) {
        return LastResponse.received().answeredBy(actor).jsonPath();
    }

    /** Negocios del listado: cada uno como mapa campo -> valor (id y name). */
    public static Question<List<Map<String, Object>>> negociosDelListado() {
        return Question.about("los negocios del catálogo").answeredBy(actor -> json(actor).getList("items"));
    }

    public static Question<Long> totalDeNegocios() {
        return Question.about("el total de negocios del catálogo")
                .answeredBy(actor -> json(actor).getLong("totalElements"));
    }

    /** Servicios del detalle de un negocio; null si la respuesta no es un detalle (por ejemplo un 404). */
    public static Question<List<Map<String, Object>>> serviciosDelNegocio() {
        return Question.about("los servicios del negocio").answeredBy(actor -> json(actor).getList("services"));
    }

    public static Question<String> nombreDelNegocio() {
        return Question.about("el nombre del negocio").answeredBy(actor -> json(actor).getString("name"));
    }
}
