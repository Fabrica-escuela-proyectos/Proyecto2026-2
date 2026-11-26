package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.models.Horario;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

import java.util.Map;

/** HU-22: el cliente reserva un servicio en una fecha y hora. */
public final class ReservarServicio {

    private ReservarServicio() {
    }

    public static Performable en(String idDelServicio, Horario horario, int duracionEnMinutos) {
        return Task.where("{0} reserva el servicio el " + horario.fechaEnLaApi() + " a las " + horario.inicioEnLaApi(),
                actor -> {
                    String token = actor.recall(Memoria.TOKEN);
                    String cuerpo = CuerpoJson.de(Map.of("serviceId", idDelServicio, "date", horario.fechaEnLaApi(),
                            "startTime", horario.inicioEnLaApi(), "endTime", horario.finEnLaApi(duracionEnMinutos)));
                    actor.attemptsTo(Post.to("/api/v1/bookings")
                            .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                                    .contentType(ContentType.JSON).body(cuerpo)));
                });
    }
}
