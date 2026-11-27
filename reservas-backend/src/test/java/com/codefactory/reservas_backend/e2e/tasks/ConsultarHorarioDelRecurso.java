package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Get;

/** HU-19 y HU-21: consulta el horario semanal vigente de un recurso. */
public final class ConsultarHorarioDelRecurso {

    private ConsultarHorarioDelRecurso() {
    }

    public static Performable delRecurso(String idDelRecurso) {
        return Task.where("{0} consulta el horario semanal del recurso", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource("/api/v1/resources/" + idDelRecurso + "/availability")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)));
        });
    }
}
