package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

import java.time.LocalDate;
import java.util.Map;

public final class CerrarRecurso {

    private CerrarRecurso() {
    }

    public static Performable entre(String idDelRecurso, LocalDate dia, String desde, String hasta) {
        return cerrar("{0} cierra el recurso el " + dia + " de " + desde + " a " + hasta, idDelRecurso,
                Map.of("start", dia + " " + desde, "end", dia + " " + hasta));
    }

    public static Performable todoElDia(String idDelRecurso, LocalDate dia) {
        return cerrar("{0} cierra el recurso todo el " + dia, idDelRecurso, Map.of("date", dia.toString()));
    }

    public static Performable todoElDiaConfirmando(String idDelRecurso, LocalDate dia) {
        return cerrar("{0} cierra el recurso todo el " + dia + " confirmando la cancelación", idDelRecurso,
                Map.of("date", dia.toString(), "confirm", true));
    }

    private static Performable cerrar(String titulo, String idDelRecurso, Map<String, Object> cuerpo) {
        return Task.where(titulo, actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Post.to("/api/v1/resources/" + idDelRecurso + "/closures")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(CuerpoJson.de(cuerpo))));
        });
    }
}
