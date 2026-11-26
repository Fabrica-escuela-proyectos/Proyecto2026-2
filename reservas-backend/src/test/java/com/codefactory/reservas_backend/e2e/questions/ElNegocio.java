package com.codefactory.reservas_backend.e2e.questions;

import io.restassured.path.json.JsonPath;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

import java.util.List;
import java.util.Map;

/** Preguntas sobre el negocio tal como lo muestra el catálogo (GET /api/v1/businesses/{id}). */
public final class ElNegocio {

    /** Los datos del negocio que se pueden editar en la HU-07. */
    public static final List<String> DATOS = List.of("name", "description", "address", "businessType", "phone");

    private ElNegocio() {
    }

    private static JsonPath cuerpo(Actor actor) {
        return LastResponse.received().answeredBy(actor).jsonPath();
    }

    public static Question<String> dato(String campo) {
        return Question.about("el dato " + campo + " del negocio")
                .answeredBy(actor -> cuerpo(actor).getString(campo));
    }

    /** Ids de los servicios activos que muestra el catálogo. */
    public static Question<List<String>> idsDeLosServicios() {
        return Question.about("los servicios que muestra el negocio")
                .answeredBy(actor -> cuerpo(actor).getList("services.id", String.class));
    }

    public static Question<Map<String, Object>> elServicio(String idDelServicio) {
        return Question.about("el servicio " + idDelServicio + " en el catálogo")
                .answeredBy(actor -> {
                    List<Map<String, Object>> servicios = cuerpo(actor).getList("services");
                    return servicios.stream().filter(s -> idDelServicio.equals(s.get("id"))).findFirst().orElse(null);
                });
    }
}
