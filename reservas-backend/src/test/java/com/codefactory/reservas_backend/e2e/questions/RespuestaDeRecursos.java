package com.codefactory.reservas_backend.e2e.questions;

import io.restassured.path.json.JsonPath;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

import java.util.List;
import java.util.Map;

/** Preguntas sobre la última respuesta en los escenarios de recursos (HU-14 a HU-17). */
public final class RespuestaDeRecursos {

    private RespuestaDeRecursos() {
    }

    private static JsonPath json(Actor actor) {
        return LastResponse.received().answeredBy(actor).jsonPath();
    }

    /** Recursos de la lista del negocio (la respuesta es un arreglo): cada uno como mapa campo -> valor. */
    public static Question<List<Map<String, Object>>> recursosDeLaLista() {
        return Question.about("los recursos de la lista").answeredBy(actor -> json(actor).getList("$"));
    }

    /** Id del recurso: "id" al registrar o editar; "resourceId" al desactivar o reactivar. */
    public static Question<String> id() {
        return Question.about("el id del recurso").answeredBy(actor -> {
            String id = json(actor).getString("id");
            return id != null ? id : json(actor).getString("resourceId");
        });
    }

    public static Question<String> idDelNegocio() {
        return Question.about("el negocio del recurso").answeredBy(actor -> json(actor).getString("businessId"));
    }

    public static Question<String> nombre() {
        return Question.about("el nombre del recurso").answeredBy(actor -> json(actor).getString("name"));
    }

    public static Question<String> tipo() {
        return Question.about("el tipo del recurso").answeredBy(actor -> json(actor).getString("type"));
    }

    public static Question<Boolean> activo() {
        return Question.about("si el recurso está activo").answeredBy(actor -> json(actor).getBoolean("active"));
    }

    /** HU-16: reservas futuras canceladas al desactivar. */
    public static Question<Integer> reservasCanceladas() {
        return Question.about("las reservas canceladas al desactivar")
                .answeredBy(actor -> json(actor).getInt("cancelledBookings"));
    }

    /** HU-16: cantidad de reservas futuras que se verían afectadas (409 CONFIRMATION_REQUIRED). */
    public static Question<String> reservasAfectadas() {
        return Question.about("las reservas futuras afectadas")
                .answeredBy(actor -> json(actor).getString("fields.affectedBookings"));
    }
}
