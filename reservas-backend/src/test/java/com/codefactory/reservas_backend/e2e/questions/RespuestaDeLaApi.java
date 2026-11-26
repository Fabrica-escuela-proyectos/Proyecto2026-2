package com.codefactory.reservas_backend.e2e.questions;

import io.restassured.response.Response;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

/** Preguntas sobre la última respuesta que recibió el actor. */
public final class RespuestaDeLaApi {

    private RespuestaDeLaApi() {
    }

    private static Response ultima(Actor actor) {
        return LastResponse.received().answeredBy(actor);
    }

    public static Question<Integer> codigoDeEstado() {
        return Question.about("el código de estado de la respuesta")
                .answeredBy(actor -> ultima(actor).statusCode());
    }

    public static Question<String> mensaje() {
        return Question.about("el mensaje de la respuesta")
                .answeredBy(actor -> ultima(actor).jsonPath().getString("message"));
    }

    public static Question<String> codigoDeError() {
        return Question.about("el código de error de la respuesta")
                .answeredBy(actor -> ultima(actor).jsonPath().getString("error"));
    }

    /** Mensaje de error de un campo concreto (por ejemplo "email" o "password"). */
    public static Question<String> mensajeDelCampo(String campo) {
        return Question.about("el mensaje de error del campo " + campo)
                .answeredBy(actor -> ultima(actor).jsonPath().getString("fields." + campo));
    }

    public static Question<String> rol() {
        return Question.about("el rol de la respuesta")
                .answeredBy(actor -> ultima(actor).jsonPath().getString("role"));
    }

    /** Id de la cuenta creada (respuesta del registro de cliente). */
    public static Question<String> idDelUsuario() {
        return Question.about("el id de la cuenta creada")
                .answeredBy(actor -> {
                    String id = ultima(actor).jsonPath().getString("id");
                    // El registro de proveedor devuelve el id de la cuenta como "userId".
                    return id != null ? id : ultima(actor).jsonPath().getString("userId");
                });
    }

    public static Question<String> idDelNegocio() {
        return Question.about("el id del negocio registrado")
                .answeredBy(actor -> ultima(actor).jsonPath().getString("businessId"));
    }

    public static Question<String> nombreDelNegocio() {
        return Question.about("el nombre del negocio registrado")
                .answeredBy(actor -> ultima(actor).jsonPath().getString("businessName"));
    }

    public static Question<String> token() {
        return Question.about("la sesión entregada")
                .answeredBy(actor -> ultima(actor).jsonPath().getString("token"));
    }
}
