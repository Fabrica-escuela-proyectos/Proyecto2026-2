package com.codefactory.reservas_backend.identity.infrastructure;

import com.codefactory.reservas_backend.identity.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Usada por JwtAuthenticationFilter (validar que la sesión del token siga
 * vigente/no revocada — HU02/HU06) y por AuthServiceImpl (HU04: cerrar
 * sesión invalidando todos los dispositivos del usuario).
 */
public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findByTokenId(String tokenId);

    List<Session> findByUserId(UUID userId);

    // HU-04, "Cierre de sesión exitoso": "la sesión activa debe quedar
    // invalidada en todos los dispositivos asociados" -> revoca todas las
    // sesiones no revocadas del usuario, no solo la que originó la petición.
    @Modifying
    @Query("UPDATE Session s SET s.revoked = true WHERE s.userId = :userId AND s.revoked = false")
    int revokeAllByUserId(@Param("userId") UUID userId);
}
