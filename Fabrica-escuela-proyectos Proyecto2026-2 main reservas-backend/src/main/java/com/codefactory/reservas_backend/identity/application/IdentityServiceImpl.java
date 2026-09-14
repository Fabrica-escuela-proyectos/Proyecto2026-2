package com.codefactory.reservas_backend.identity.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementación de IdentityService basada en el
 * {@link org.springframework.security.core.context.SecurityContext} de
 * Spring Security.
 *
 * IMPORTANTE — alcance real en Sprint 1: ningún filtro llena todavía el
 * SecurityContext (HU02/login no es tarea individual de Simon y
 * SecurityConfig no tiene un JwtAuthenticationFilter conectado). Por lo
 * tanto, en este sprint {@link #getCurrentUser()} siempre devuelve
 * {@code Optional.empty()} — es un comportamiento correcto y esperado, no
 * un bug, mientras no exista login. Se deja implementado (en vez de un
 * "TODO" vacío) para que HU02 solo tenga que conectar el filtro que puebla
 * el SecurityContext; este método no debería necesitar cambios.
 */
@Service
public class IdentityServiceImpl implements IdentityService {

    @Override
    public Optional<UserIdentity> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof UserIdentity userIdentity) {
            return Optional.of(userIdentity);
        }
        // Ningún proveedor de autenticación en Sprint 1 coloca un
        // UserIdentity como principal; queda documentado como el contrato
        // que HU02 debe cumplir al implementar su filtro/AuthenticationProvider.
        return Optional.empty();
    }

    @Override
    public boolean isAuthenticated() {
        return getCurrentUser().isPresent();
    }
}
