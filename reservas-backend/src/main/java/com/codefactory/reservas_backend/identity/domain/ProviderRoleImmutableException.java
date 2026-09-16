package com.codefactory.reservas_backend.identity.domain;

/**
 * HU-05, escenario "Administrador intenta cambiar un rol Proveedor de un
 * usuario existente": el rol de un usuario que ya tiene PROVEEDOR no se
 * puede modificar por este endpoint, sin importar cuál sea el nuevo rol
 * solicitado. Equivalente en la capa de aplicación al trigger
 * fn_block_provider_role_change descrito en el modelo físico de BD
 * (03_modelo_fisico.md); aquí se aplica en el servicio porque el esquema
 * Flyway real (V1/V2) no incluye ese trigger — ver docs/estado-proyecto-sprint-1.md
 * sección "Riesgos" sobre los dos modelos de BD sin conciliar.
 */
public class ProviderRoleImmutableException extends RuntimeException {
    public ProviderRoleImmutableException(String message) {
        super(message);
    }
}
