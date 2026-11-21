package com.codefactory.reservas_backend.e2e.interactions;

import io.restassured.specification.RequestSpecification;

import java.util.function.Function;

/** Interacción auxiliar: agrega la sesión (Bearer) a una petición. La reutilizarán las HU que exigen estar autenticado. */
public final class ConSesion {

    private ConSesion() {
    }

    public static Function<RequestSpecification, RequestSpecification> deAcceso(String token) {
        return request -> request.header("Authorization", "Bearer " + token);
    }
}
