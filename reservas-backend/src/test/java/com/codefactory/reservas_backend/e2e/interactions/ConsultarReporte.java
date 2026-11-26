package com.codefactory.reservas_backend.e2e.interactions;

import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Interaction;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.rest.interactions.Get;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConsultarReporte {

    private ConsultarReporte() {
    }

    // los parametros nulos no se envian: asi se prueba la consulta sin periodo
    public static Performable en(String ruta, Map<String, String> parametros) {
        Map<String, String> enviados = new LinkedHashMap<>();
        parametros.forEach((clave, valor) -> {
            if (valor != null) {
                enviados.put(clave, valor);
            }
        });
        return Interaction.where("{0} consulta " + ruta + " con " + enviados, actor -> {
            String token = actor.recall(Memoria.TOKEN);
            actor.attemptsTo(Get.resource(ruta)
                    .with(request -> ConSesion.deAccesoOpcional(token).apply(request).queryParams(enviados)));
        });
    }

    public static Map<String, String> periodo(String desde, String hasta) {
        Map<String, String> parametros = new LinkedHashMap<>();
        parametros.put("from", desde);
        parametros.put("to", hasta);
        return parametros;
    }
}
