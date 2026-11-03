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
    public static final String REPORTS = "Reportes";

    static final List<Tag> ALL = List.of(
            new Tag().name(AUTH).description("Inicio y cierre de sesión (HU-02, HU-04) y enrolamiento de MFA (ADR-004)"),
            new Tag().name(USERS).description("Registro de clientes (HU-01) y gestión de cuentas por el administrador (HU-05, HU-06, HU-28)"),
            new Tag().name(PROVIDERS).description("Registro de proveedores (HU-03), su perfil (HU-06), la información del negocio (HU-07) y la antelación mínima de reserva (HU-08)"),
            new Tag().name(CATALOG).description("Consulta de negocios y servicios (HU-13) y de horarios libres de un servicio (HU-20, pública)"),
            new Tag().name(SERVICES).description("Servicios del negocio: crear (HU-09), editar (HU-10), desactivar y reactivar (HU-11, HU-12) y recursos que los atienden (HU-18)"),
            new Tag().name(RESOURCES).description("Recursos del negocio: registrar (HU-14), editar (HU-15), desactivar y reactivar (HU-16, HU-17), horarios (HU-19) y cierres puntuales (HU-21)"),
            new Tag().name(CLIENT_BOOKINGS).description("Crear (HU-22), consultar (HU-23) y cancelar (HU-25) reservas como cliente"),
            new Tag().name(PROVIDER_BOOKINGS).description("Consultar las reservas del negocio (HU-24), cancelarlas (HU-26) y completarlas (HU-27)"),
            new Tag().name(REPORTS).description("Ocupación, reservas por estado, servicios más reservados y valor del negocio (HU-29..33) y resumen de la plataforma para el administrador (HU-34)"));

    private OpenApiTags() {
    }
}
