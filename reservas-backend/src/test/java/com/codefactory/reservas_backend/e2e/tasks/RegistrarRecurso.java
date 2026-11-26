package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

import java.util.Map;

/** HU-14: el proveedor registra un recurso con el nombre indicado en su negocio (tipo SALA). */
public final class RegistrarRecurso {

    private RegistrarRecurso() {
    }

    public static Performable llamado(String nombre, String idDelNegocio) {
        return Task.where("{0} registra el recurso " + nombre, actor -> {
            String token = actor.recall(Memoria.TOKEN);
            String cuerpo = CuerpoJson.de(Map.of("name", nombre, "type", "SALA"));
            actor.attemptsTo(Post.to("/api/v1/businesses/" + idDelNegocio + "/resources")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(cuerpo)));
        });
    }
}
