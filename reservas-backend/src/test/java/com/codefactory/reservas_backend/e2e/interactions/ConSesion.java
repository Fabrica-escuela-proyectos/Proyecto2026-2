package com.codefactory.reservas_backend.e2e.interactions;

import io.restassured.specification.RequestSpecification;

import java.util.function.Function;

/** Interacción auxiliar: agrega la sesión (Bearer) a una petición. La reutilizarán las HU que exigen estar autenticado. */
public final class ConSesion {

    private ConSesion() {
    }

    /** Igual que deAcceso, pero si no hay sesión (nula o vacía) envía la petición sin ella. */
    public static Function<RequestSpecification, RequestSpecification> deAccesoOpcional(String token) {
        return request -> token == null || token.isBlank() ? request : deAcceso(token).apply(request);
    }

    public static Function<RequestSpecification, RequestSpecification> deAcceso(String token) {
        return request -> request.header("Authorization", "Bearer " + token);
    }
}
