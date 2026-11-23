package com.codefactory.reservas_backend.e2e.user;

/**
 * Actores de los escenarios de reservas y reportes. CONTEXTO no actúa: solo guarda los datos del escenario
 * (agenda, reservas, periodo) para que los pasos de distintas clases los compartan.
 * Ojo: theActorCalled(...) pone a ese actor en el foco; los pasos que usan theActorInTheSpotlight() (como
 * "access is denied") deben ir justo después de la acción y leer el CONTEXTO solo después de comprobar.
 */
public final class PersonajesDeReservas {

    public static final String PROVEEDOR = "Proveedor del negocio";
    public static final String CLIENTE = "Cliente";
    public static final String OTRO_CLIENTE = "Otro cliente";
    public static final String CONTEXTO = "Contexto del escenario";

    private PersonajesDeReservas() {
    }
}
