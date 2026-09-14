package com.codefactory.reservas_backend.identity.application;

import java.util.UUID;

/**
 * Contrato de autorización (interfaces-modulos-sprint-1.md sección 3).
 *
 * Nota de ubicación — a validar con el equipo (ver
 * docs/matriz-actualizaciones.md): el diagrama de componentes
 * (diagrama-componentes.puml) dibuja "Authorization Service" como un
 * servicio transversal separado, al mismo nivel que Audit. Sin embargo, el
 * diagrama de paquetes (diagrama-paquetes.puml), que es la vista física
 * autoritativa según arquitectura-sprint-1.md sección 8.3, NO define un
 * paquete "authorization" independiente — solo identity, provider, service,
 * resource, reservation, report y audit. Se interpretó, con apoyo del texto
 * de la sección 6.2 ("Identidad y Acceso... será utilizado por los demás
 * módulos... para determinar... si tiene autorización"), que Authorization
 * Service es lógicamente transversal pero se implementa físicamente dentro
 * de identity.application, expuesto igual que IdentityService. Si el
 * equipo prefiere un paquete propio, es un cambio mecánico de mover estas
 * dos clases.
 *
 * Los nombres y tipos de parámetro del contrato original usan
 * {@code Long userId}; aquí se adaptó a {@code UUID} porque esa es la clave
 * primaria real de User en Sprint 1 (ver identity.domain.User). El propio
 * documento aclara que es "el contrato conceptual" y que los tipos podrán
 * ajustarse durante la implementación.
 */
public interface AuthorizationService {

    boolean hasRole(UUID userId, String role);

    boolean hasPermission(UUID userId, String permission);
}
