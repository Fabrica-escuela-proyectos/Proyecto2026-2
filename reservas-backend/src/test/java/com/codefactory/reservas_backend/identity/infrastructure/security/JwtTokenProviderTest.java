package com.codefactory.reservas_backend.identity.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de la utilidad de JWT preparada como insumo para HU02 (ver
 * Javadoc de JwtTokenProvider). Verifica lo que ADR-002 exige: firma
 * válida, expiración máxima de 1 hora y un jti único por token.
 */
class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider("prueba-clave-secreta-de-al-menos-32-caracteres");

    @Test
    void debeEmitirTokenConExpiracionDeUnaHora() {
        // Arrange
        Instant antes = Instant.now();

        // Act
        JwtTokenProvider.IssuedToken issued = provider.issueToken("usuario-123");

        // Assert
        Duration duracion = Duration.between(antes, issued.expiresAt());
        assertThat(duracion).isCloseTo(Duration.ofHours(1), Duration.ofSeconds(5));
    }

    @Test
    void debeGenerarUnJtiDiferentePorCadaToken() {
        // Act
        JwtTokenProvider.IssuedToken primero = provider.issueToken("usuario-123");
        JwtTokenProvider.IssuedToken segundo = provider.issueToken("usuario-123");

        // Assert
        assertThat(primero.tokenId()).isNotEqualTo(segundo.tokenId());
    }

    @Test
    void debeValidarUnTokenRecienEmitidoYRecuperarElSubject() {
        // Arrange
        JwtTokenProvider.IssuedToken issued = provider.issueToken("usuario-456");

        // Act
        Claims claims = provider.parseAndValidate(issued.token());

        // Assert
        assertThat(claims.getSubject()).isEqualTo("usuario-456");
        assertThat(claims.getId()).isEqualTo(issued.tokenId());
    }

    @Test
    void debeRechazarUnTokenManipulado() {
        // Arrange
        JwtTokenProvider.IssuedToken issued = provider.issueToken("usuario-789");
        String tokenManipulado = issued.token() + "manipulado";

        // Act / Assert
        assertThatThrownBy(() -> provider.parseAndValidate(tokenManipulado))
                .isInstanceOf(JwtException.class);
    }
}
