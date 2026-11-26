package com.codefactory.reservas_backend.e2e.tasks;

import com.codefactory.reservas_backend.e2e.models.Roles;
import com.codefactory.reservas_backend.e2e.models.DatosDeRegistro;
import com.codefactory.reservas_backend.e2e.models.Json;
import com.codefactory.reservas_backend.e2e.user.Memoria;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.rest.interactions.Post;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** HU-01 y HU-03: el actor envía el formulario de registro. */
public final class RegistrarCuenta {

    private RegistrarCuenta() {
    }

    /** Registra como proveedor si los datos traen nombre de negocio (aunque esté vacío), y como cliente si no. */
    public static Performable conDatos(DatosDeRegistro datos) {
        return datos.nombreDelNegocio() != null ? comoProveedor(datos) : comoCliente(datos);
    }

    /** HU-03: intenta registrarse como proveedor pidiendo, además, un rol distinto (por ejemplo "Administrador"). */
    public static Performable comoProveedorSolicitandoRol(DatosDeRegistro datos, String rolSolicitado) {
        return Task.where("{0} envía su registro como proveedor pidiendo el rol " + rolSolicitado, actor -> {
            actor.remember(Memoria.DATOS, datos);
            Map<String, String> cuerpo = new LinkedHashMap<>(datos.comoCuerpo());
            cuerpo.put("role", Roles.nombreEnLaApi(rolSolicitado));
            actor.attemptsTo(Post.to("/api/v1/providers").with(request -> request.body(Json.de(cuerpo))));
        });
    }

    public static Performable comoCliente(DatosDeRegistro datos) {
        return Task.where("{0} envía su registro como cliente", actor -> {
            actor.remember(Memoria.DATOS, datos);
            actor.attemptsTo(Post.to("/api/v1/users").with(request -> request.body(Json.de(datos.comoCuerpo()))));
        });
    }

    public static Performable comoProveedor(DatosDeRegistro datos) {
        return Task.where("{0} envía su registro como proveedor", actor -> {
            actor.remember(Memoria.DATOS, datos);
            actor.attemptsTo(Post.to("/api/v1/providers").with(request -> request.body(Json.de(datos.comoCuerpo()))));
        });
    }
}
