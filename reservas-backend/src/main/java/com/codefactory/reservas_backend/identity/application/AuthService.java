package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.identity.controller.dto.LoginRequest;
import com.codefactory.reservas_backend.identity.controller.dto.LoginResponse;

import java.util.UUID;

/**
 * HU-02 (Inicio de sesión) y HU-04 (Cerrar sesión).
 */
public interface AuthService {

    LoginResponse login(LoginRequest request, String originIp);

    /**
     * Revoca todas las sesiones activas del usuario (HU-04: "la sesión
     * activa debe quedar invalidada en todos los dispositivos asociados").
     */
    void logout(UUID userId, String originIp);
}
