package com.codefactory.reservas_backend.identity.infrastructure.security;

import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.identity.domain.Session;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.SessionRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puebla el SecurityContext a partir del JWT recibido en el header
 * Authorization, cerrando el hueco que IdentityServiceImpl y SecurityConfig
 * dejaban documentado como pendiente ("ningún filtro llena todavía el
 * SecurityContext") — implementa el flujo descrito en
 * ADR-002-autenticacion-y-sesiones.md sección 3 ("Solicitud protegida -> JWT
 * -> Spring Security -> Usuario autenticado -> Autorización").
 *
 * Un token se considera válido para autenticar la petición solo si:
 * 1. La firma y expiración del JWT son válidas (JwtTokenProvider).
 * 2. Existe una fila en `sessions` con ese jti y sigue vigente, ni revocada
 *    ni expirada (Session.isValid) — así HU04 (logout) y la expiración de
 *    HU02/HU06 funcionan aunque el JWT en sí todavía no haya expirado.
 * 3. El usuario referenciado por el subject sigue existiendo y habilitado
 *    (cubre HU05: un usuario eliminado no puede seguir usando sesiones
 *    viejas; y HU06: los roles se leen frescos de BD en cada petición, así
 *    que un cambio de rol aplica de inmediato sin esperar a que expire el
 *    token).
 *
 * Si cualquiera de estos pasos falla, la petición sigue sin autenticar (no
 * se lanza excepción aquí); es SecurityConfig quien decide, vía
 * RestAuthenticationEntryPoint, si esa ruta exige autenticación.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        extractToken(request).flatMap(this::authenticate)
                .ifPresent(authentication -> SecurityContextHolder.getContext().setAuthentication(authentication));

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private Optional<UsernamePasswordAuthenticationToken> authenticate(String token) {
        try {
            Claims claims = jwtTokenProvider.parseAndValidate(token);
            UUID userId = UUID.fromString(claims.getSubject());

            Optional<Session> session = sessionRepository.findByTokenId(claims.getId());
            if (session.isEmpty() || !session.get().isValid(Instant.now())) {
                return Optional.empty();
            }

            return userRepository.findById(userId)
                    .filter(User::isEnabled)
                    .flatMap(this::toAuthentication);
        } catch (JwtException | IllegalArgumentException invalidToken) {
            // Firma inválida, token expirado, manipulado o subject/jti con
            // formato inesperado: se trata igual que "no autenticado".
            return Optional.empty();
        }
    }

    private Optional<UsernamePasswordAuthenticationToken> toAuthentication(User user) {
        String role = user.getRoles().stream()
                .findFirst()
                .map(r -> r.getName().name())
                .orElse(null);
        if (role == null) {
            return Optional.empty();
        }

        UserIdentity principal = new UserIdentity(user.getId(), user.getEmail(), role);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return Optional.of(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
