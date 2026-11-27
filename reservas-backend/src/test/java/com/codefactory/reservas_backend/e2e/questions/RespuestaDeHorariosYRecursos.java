package com.codefactory.reservas_backend.e2e.questions;

import io.restassured.path.json.JsonPath;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Preguntas compartidas sobre respuestas de recursos asignados, horarios semanales, cierres y reservas. */
public final class RespuestaDeHorariosYRecursos {

    private RespuestaDeHorariosYRecursos() {
    }

    private static JsonPath json(Actor actor) {
        return LastResponse.received().answeredBy(actor).jsonPath();
    }

    /** HU-18: ids de los recursos asignados al servicio. */
    public static Question<List<String>> idsDeRecursosAsignados() {
        return Question.about("los ids de los recursos asignados")
                .answeredBy(actor -> json(actor).getList("resources.id", String.class));
    }

    /** HU-19 y HU-21: mapa dia ISO (1..7) -> lista de rangos {start, end} del horario semanal. */
    @SuppressWarnings("unchecked")
    public static Question<Map<Integer, List<Map<String, String>>>> horarioSemanal() {
        return Question.about("el horario semanal del recurso").answeredBy(actor -> {
            List<Map<String, Object>> dias = json(actor).getList("days");
            Map<Integer, List<Map<String, String>>> resultado = new LinkedHashMap<>();
            if (dias != null) {
                for (Map<String, Object> dia : dias) {
                    int numero = ((Number) dia.get("dayOfWeek")).intValue();
                    List<Map<String, String>> rangos = (List<Map<String, String>>) dia.get("ranges");
                    resultado.put(numero, rangos == null ? List.of() : rangos);
                }
            }
            return resultado;
        });
    }

    /** HU-21: cantidad de reservas afectadas informada en el error 409 CONFIRMATION_REQUIRED. */
    public static Question<String> reservasAfectadas() {
        return Question.about("la cantidad de reservas afectadas")
                .answeredBy(actor -> json(actor).getString("fields.affectedBookings"));
    }

    /** HU-21: id del cierre creado en la respuesta 201. */
    public static Question<String> idDelCierre() {
        return Question.about("el id del cierre creado")
                .answeredBy(actor -> json(actor).getString("closure.id"));
    }

    /** HU-22: id de la reserva recién creada en la respuesta 201. */
    public static Question<String> idDeLaReserva() {
        return Question.about("el id de la reserva creada")
                .answeredBy(actor -> json(actor).getString("id"));
    }
}
