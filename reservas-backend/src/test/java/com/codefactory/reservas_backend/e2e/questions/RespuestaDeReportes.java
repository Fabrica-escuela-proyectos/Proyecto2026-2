package com.codefactory.reservas_backend.e2e.questions;

import io.restassured.path.json.JsonPath;
import io.restassured.path.json.config.JsonPathConfig;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RespuestaDeReportes {

    // con DOUBLE los porcentajes llegan exactos (80.0, 18.8) y no como float
    private static final JsonPathConfig DECIMALES =
            JsonPathConfig.jsonPathConfig().numberReturnType(JsonPathConfig.NumberReturnType.DOUBLE);

    private RespuestaDeReportes() {
    }

    private static JsonPath json(Actor actor) {
        return LastResponse.received().answeredBy(actor).jsonPath(DECIMALES);
    }

    public static Question<Double> porcentajeGeneral() {
        return Question.about("el porcentaje de ocupacion general")
                .answeredBy(actor -> decimal(json(actor).get("occupancyPercent")));
    }

    public static Question<Long> numero(String campo) {
        return Question.about("el valor de " + campo).answeredBy(actor -> entero(json(actor).get(campo)));
    }

    public static Question<String> texto(String campo) {
        return Question.about("el campo " + campo).answeredBy(actor -> json(actor).getString(campo));
    }

    public static Question<List<String>> nombresDeLosRecursos() {
        return Question.about("los recursos del reporte, en orden")
                .answeredBy(actor -> json(actor).getList("resources.name", String.class));
    }

    public static Question<Double> porcentajeDelRecurso(String nombre) {
        return Question.about("el porcentaje de uso de " + nombre)
                .answeredBy(actor -> decimal(fila(actor, "resources", "name", nombre).get("occupancyPercent")));
    }

    public static Question<Long> minutosDisponiblesDelRecurso(String nombre) {
        return Question.about("los minutos disponibles de " + nombre)
                .answeredBy(actor -> entero(fila(actor, "resources", "name", nombre).get("availableMinutes")));
    }

    public static Question<List<Map<String, Object>>> periodos() {
        return Question.about("los periodos del reporte").answeredBy(actor -> json(actor).getList("periods"));
    }

    public static Question<List<String>> nombresDeLosServicios() {
        return Question.about("los servicios del reporte, en orden")
                .answeredBy(actor -> json(actor).getList("services.serviceName", String.class));
    }

    public static Question<Long> reservasDelServicio(String nombre) {
        return Question.about("las reservas de " + nombre)
                .answeredBy(actor -> entero(fila(actor, "services", "serviceName", nombre).get("bookings")));
    }

    private static Map<String, Object> fila(Actor actor, String lista, String campo, String valor) {
        List<Map<String, Object>> filas = json(actor).getList(lista);
        return filas.stream().filter(f -> Objects.equals(valor, f.get(campo))).findFirst()
                .orElseThrow(() -> new AssertionError(valor + " no aparece en " + lista + ": " + filas));
    }

    public static Double decimal(Object valor) {
        return valor instanceof Number numero ? numero.doubleValue() : null;
    }

    public static Long entero(Object valor) {
        return valor instanceof Number numero ? numero.longValue() : null;
    }
}
