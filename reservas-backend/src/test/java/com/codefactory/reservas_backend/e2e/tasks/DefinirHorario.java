package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Put;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** HU-19: el proveedor define el mismo horario de atención para los 7 días de la semana de un recurso. */
public final class DefinirHorario {

    private DefinirHorario() {
    }

    public static Performable todaLaSemana(String idDelRecurso, String desde, String hasta) {
        return Task.where("{0} define el horario del recurso de " + desde + " a " + hasta, actor -> {
            String token = actor.recall(Memoria.TOKEN);
            List<Map<String, Object>> dias = IntStream.rangeClosed(1, 7)
                    .mapToObj(dia -> Map.<String, Object>of("dayOfWeek", dia,
                            "ranges", List.of(Map.of("start", desde, "end", hasta))))
                    .toList();
            String cuerpo = CuerpoJson.de(Map.of("days", dias));
            actor.attemptsTo(Put.to("/api/v1/resources/" + idDelRecurso + "/availability")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(cuerpo)));
        });
    }
}
