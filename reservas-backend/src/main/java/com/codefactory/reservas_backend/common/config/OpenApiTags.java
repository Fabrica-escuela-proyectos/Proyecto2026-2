package com.codefactory.reservas_backend.common.config;

import io.swagger.v3.oas.models.tags.Tag;

import java.util.List;

/**
 * Grupos de Swagger UI. Los nombres son constantes porque {@code @Tag(name = ...)} las exige; el orden de
 * {@link #ALL} es el orden en que aparecen en la interfaz.
 */
public final class OpenApiTags {

    public static final String AUTH = "Autenticación y MFA";
    public static final String USERS = "Usuarios";
    public static final String PROVIDERS = "Proveedores y negocio";
    public static final String CATALOG = "Catálogo y disponibilidad";
    public static final String SERVICES = "Servicios";
    public static final String RESOURCES = "Recursos";
    public static final String CLIENT_BOOKINGS = "Reservas del cliente";
    public static final String PROVIDER_BOOKINGS = "Reservas del proveedor";

    static final List<Tag> ALL = List.of(
            new Tag().name(AUTH).description("Inicio y cierre de sesión (HU-02, HU-04) y enrolamiento de MFA (ADR-004)"),
            new Tag().name(USERS).description("Registro de clientes (HU-01) y gestión de cuentas por el administrador (HU-05, HU-06, HU-28)"),
            new Tag().name(PROVIDERS).description("Registro de proveedores (HU-03), su perfil (HU-06) y la antelación mínima de reserva (HU-08)"),
            new Tag().name(CATALOG).description("Consulta de negocios y servicios (HU-13) y de horarios libres de un servicio (HU-20, pública)"),
            new Tag().name(SERVICES).description("Servicios del negocio (HU-09) y recursos que los atienden (HU-18)"),
            new Tag().name(RESOURCES).description("Recursos del negocio (HU-14), sus horarios (HU-19) y su desactivación o reactivación (HU-16, HU-17)"),
            new Tag().name(CLIENT_BOOKINGS).description("Crear (HU-22), consultar (HU-23) y cancelar (HU-25) reservas como cliente"),
            new Tag().name(PROVIDER_BOOKINGS).description("Consultar las reservas del negocio (HU-24) y cancelarlas (HU-26)"));

    private OpenApiTags() {
    }
}
