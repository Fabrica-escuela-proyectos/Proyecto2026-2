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

/** HU-18: el proveedor asigna un recurso a un servicio. */
public final class AsignarRecursos {

    private AsignarRecursos() {
    }

    public static Performable alServicio(String idDelServicio, String idDelRecurso) {
        return Task.where("{0} asigna un recurso al servicio", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            String cuerpo = CuerpoJson.de(Map.of("resourceIds", List.of(idDelRecurso)));
            actor.attemptsTo(Put.to("/api/v1/services/" + idDelServicio + "/resources")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(cuerpo)));
        });
    }

    public static Performable alServicio(String idDelServicio, List<String> idsDeRecursos) {
        return Task.where("{0} asigna los recursos al servicio", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            String cuerpo = CuerpoJson.de(Map.of("resourceIds", idsDeRecursos));
            actor.attemptsTo(Put.to("/api/v1/services/" + idDelServicio + "/resources")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(cuerpo)));
        });
    }
}
