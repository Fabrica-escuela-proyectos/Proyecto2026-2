package com.codefactory.reservas_backend.e2e.questions;

import io.restassured.path.json.JsonPath;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

import java.util.List;
import java.util.Map;

/** Preguntas sobre la última respuesta en los escenarios de reservas y reportes. */
public final class RespuestaDeReservas {

    /** Nombres posibles del campo con la cantidad de reservas del reporte de HU-33 (hoy la API no trae ninguno). */
    private static final List<String> CAMPOS_DE_CANTIDAD =
            List.of("count", "bookings", "bookingCount", "bookingsCount", "totalBookings", "completedCount");

    private RespuestaDeReservas() {
    }

    private static JsonPath json(Actor actor) {
        return LastResponse.received().answeredBy(actor).jsonPath();
    }

    public static Question<String> estado() {
        return Question.about("el estado de la reserva").answeredBy(actor -> json(actor).getString("status"));
    }

    /** Reservas de una lista paginada (HU-23 y HU-24): cada una como mapa campo -> valor. */
    public static Question<List<Map<String, Object>>> reservasDeLaLista() {
        return Question.about("las reservas de la lista").answeredBy(actor -> json(actor).getList("items"));
    }

    /** Horas de inicio de los horarios libres (HU-20), por ejemplo "10:00". */
    public static Question<List<String>> horariosLibres() {
        return Question.about("los horarios libres").answeredBy(actor -> json(actor).getList("slots.start", String.class));
    }

    /** HU-33: valor total del periodo en pesos. */
    public static Question<Long> valorTotal() {
        return Question.about("el valor total de las reservas").answeredBy(actor -> {
            Number valor = json(actor).get("totalValue");
            return valor == null ? null : valor.longValue();
        });
    }

    /** HU-33: cantidad de reservas incluidas en el total, si la respuesta la trae (null si no). */
    public static Question<Long> cantidadDeReservas() {
        return Question.about("la cantidad de reservas incluidas en el total").answeredBy(actor -> {
            JsonPath json = json(actor);
            for (String campo : CAMPOS_DE_CANTIDAD) {
                Object valor = json.get(campo);
                if (valor instanceof Number numero) {
                    return numero.longValue();
                }
            }
            return null;
        });
    }
}
