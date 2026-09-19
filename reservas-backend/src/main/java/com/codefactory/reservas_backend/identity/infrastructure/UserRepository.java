package com.codefactory.reservas_backend.identity.infrastructure;

import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Escenario "Registro con correo ya existente"
    boolean existsByEmailIgnoreCase(String email);

    // Escenario "Registro con número de celular ya registrado en otra cuenta"
    boolean existsByCellphone(String cellphone);

    // Reutilizada por HU02 (login) según endpoints-sprint-1.md ("Buscar el
    // usuario mediante su email").
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByRoles_Name(RoleName name);
}
