package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.interactions.ConSesion;
import com.codefactory.reservas_backend.e2e.models.CuerpoJson;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import io.restassured.http.ContentType;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

import java.util.HashMap;
import java.util.Map;

/** HU-25 (cliente) y HU-26 (proveedor): cancelar una reserva. */
public final class CancelarReserva {

    private CancelarReserva() {
    }

    /** HU-25: el cliente cancela su reserva (sin motivo). */
    public static Performable comoCliente(String idDeLaReserva) {
        return Task.where("{0} cancela la reserva como cliente", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Post.to("/api/v1/bookings/" + idDeLaReserva + "/cancellation")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body("{}")));
        });
    }

    /** HU-26: el proveedor cancela una reserva de su negocio indicando el motivo (puede ir vacío para probar el rechazo). */
    public static Performable comoProveedor(String idDeLaReserva, String motivo) {
        return Task.where("{0} cancela la reserva como proveedor", actor -> {
            String token = actor.recall(Memoria.TOKEN);
            Map<String, Object> cuerpo = new HashMap<>();
            cuerpo.put("reason", motivo);
            actor.attemptsTo(Post.to("/api/v1/bookings/" + idDeLaReserva + "/provider-cancellation")
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request)
                            .contentType(ContentType.JSON).body(CuerpoJson.de(cuerpo))));
        });
    }
}
