package com.codefactory.reservas_backend.e2e.questions;

import io.restassured.path.json.JsonPath;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

/**
 * Preguntas sobre el servicio que devolvió la API en la última respuesta del actor (al crearlo, editarlo,
 * desactivarlo o reactivarlo).
 */
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

    public static Question<Long> precio() {
        return Question.about("el precio del servicio")
                .answeredBy(actor -> cuerpo(actor).getLong("priceCop"));
    }

    public static Question<String> descripcion() {
        return Question.about("la descripción del servicio")
                .answeredBy(actor -> cuerpo(actor).getString("description"));
    }

    /** Al crear o editar el id viene en "id"; al desactivar viene en "serviceId". */
    public static Question<String> id() {
        return Question.about("el id del servicio")
                .answeredBy(actor -> {
                    String id = cuerpo(actor).getString("id");
                    return id != null ? id : cuerpo(actor).getString("serviceId");
                });
    }

    public static Question<String> nombre() {
        return Question.about("el nombre del servicio")
                .answeredBy(actor -> cuerpo(actor).getString("name"));
    }
}
