package com.codefactory.reservas_backend.identidadAcceso.repository;

import com.codefactory.reservas_backend.identidadAcceso.modelo.User;
import org.springframework.data.jpa.repository.JpaRepository;
 
import java.util.Optional;
import java.util.UUID;
 
public interface UserRepository extends JpaRepository<User, UUID> {
 
    // Escenario "Registro con correo ya existente"
    boolean existsByEmailIgnoreCase(String email);
 
    // Escenario "Registro con numero de celular ya registrado en otra cuenta"
    boolean existsByPhoneNumber(String phoneNumber);
 
    // Reutilizada por HU02 (login) segun la Matriz tecnica ("Buscar usuario por email")
    Optional<User> findByEmailIgnoreCase(String email);
}
