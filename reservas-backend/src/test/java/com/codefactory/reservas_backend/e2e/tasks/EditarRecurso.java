package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.models.DatosDeRecurso;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Patch;

/** HU-15: el proveedor edita el nombre y/o el tipo de un recurso (edición parcial: lo nulo no se envía). */
public final class EditarRecurso {

    private EditarRecurso() {
    }

    public static Performable con(String idDelRecurso, DatosDeRecurso cambios) {
        return Task.where("{0} edita un recurso", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Patch.to("/api/v1/resources/" + idDelRecurso)
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(CuerpoJson.de(cambios.comoCuerpo()))));
        });
    }
}
