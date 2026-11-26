package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

import java.util.Map;

/** HU-09 (apoyo de HU-13): el proveedor crea un servicio con descripción, para que el catálogo la muestre. */
public final class CrearServicioConDescripcion {

    private CrearServicioConDescripcion() {
    }

    public static Performable enElNegocio(String idDelNegocio, String nombre, String descripcion,
                                          int duracionEnMinutos, long precio) {
        return Task.where("{0} crea el servicio " + nombre, actor -> {
            String token = actor.recall(Memoria.TOKEN);
            String cuerpo = CuerpoJson.de(Map.of("name", nombre, "description", descripcion,
                    "durationMinutes", duracionEnMinutos, "priceCop", precio));
            actor.attemptsTo(Post.to("/api/v1/businesses/" + idDelNegocio + "/services")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(cuerpo)));
        });
    }
}
