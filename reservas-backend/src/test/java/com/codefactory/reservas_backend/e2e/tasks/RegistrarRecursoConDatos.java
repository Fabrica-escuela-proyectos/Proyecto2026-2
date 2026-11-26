package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.models.DatosDeRecurso;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

/** HU-14: el actor registra un recurso en un negocio con los datos indicados (pueden faltar el nombre o el tipo). */
public final class RegistrarRecursoConDatos {

    private RegistrarRecursoConDatos() {
    }

    public static Performable en(String idDelNegocio, DatosDeRecurso datos) {
        return Task.where("{0} registra un recurso en un negocio", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Post.to("/api/v1/businesses/" + idDelNegocio + "/resources")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(CuerpoJson.de(datos.comoCuerpo()))));
        });
    }
}
