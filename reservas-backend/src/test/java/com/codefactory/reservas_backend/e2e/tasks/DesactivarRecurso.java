package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

import java.util.Map;

/**
 * HU-16: el proveedor pide desactivar un recurso. "confirmar" nulo envía el cuerpo vacío (la primera solicitud, sin
 * confirmar); true confirma la cancelación de las reservas futuras; false es decir explícitamente que no.
 */
public final class DesactivarRecurso {

    private DesactivarRecurso() {
    }

    public static Performable conId(String idDelRecurso, Boolean confirmar) {
        return Task.where("{0} solicita desactivar un recurso", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            String cuerpo = confirmar == null ? "{}" : CuerpoJson.de(Map.of("confirm", confirmar));
            actor.attemptsTo(Post.to("/api/v1/resources/" + idDelRecurso + "/deactivation")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(cuerpo)));
        });
    }
}
