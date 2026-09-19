package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.audit.application.AuditService;
import com.codefactory.reservas_backend.audit.domain.AuditEventType;
import com.codefactory.reservas_backend.common.validation.PasswordValidator;
import com.codefactory.reservas_backend.common.validation.PhoneValidator;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Crea el primer ADMINISTRADOR al arrancar, resolviendo el problema de que
 * ninguna ruta pública asigna ese rol y las rutas de administración solo las
 * puede usar alguien que ya lo tenga.
 *
 * Solo actúa si (1) hay variables BOOTSTRAP_ADMIN_* definidas y (2) todavía
 * no existe ningún ADMINISTRADOR, así que reiniciar la app no duplica ni
 * modifica nada. No expone ninguna ruta HTTP. La contraseña se toma del
 * entorno, nunca del repositorio, y nunca se escribe en logs.
 *
 * Si las variables están definidas pero incompletas o inválidas, el arranque
 * falla a propósito: es mejor enterarse en el despliegue que descubrir después
 * que nadie puede administrar el sistema.
 */
@Slf4j
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final String DEFAULT_FULL_NAME = "Administrador inicial";

    private final UserRepository userRepository;
    private final UserProvisioningService provisioningService;
    private final AuditService auditService;
    private final String email;
    private final String password;
    private final String cellphone;
    private final String fullName;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            UserProvisioningService provisioningService,
            AuditService auditService,
            @Value("${bootstrap.admin.email:}") String email,
            @Value("${bootstrap.admin.password:}") String password,
            @Value("${bootstrap.admin.cellphone:}") String cellphone,
            @Value("${bootstrap.admin.full-name:}") String fullName) {
        this.userRepository = userRepository;
        this.provisioningService = provisioningService;
        this.auditService = auditService;
        this.email = email;
        this.password = password;
        this.cellphone = cellphone;
        this.fullName = fullName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(email) && isBlank(password) && isBlank(cellphone)) {
            return;
        }
        if (userRepository.existsByRoles_Name(RoleName.ADMINISTRADOR)) {
            log.info("Bootstrap de administrador omitido: ya existe un usuario con rol ADMINISTRADOR");
            return;
        }

        validateConfiguration();

        String name = isBlank(fullName) ? DEFAULT_FULL_NAME : fullName;
        provisioningService.provisionUser(name, email, cellphone, password, RoleName.ADMINISTRADOR);
        auditService.registerEvent(AuditEventType.OPERACION_SENSIBLE, email, "SUCCESS",
                "Administrador inicial creado por bootstrap", "bootstrap");
        log.info("Administrador inicial creado: {}", email);
    }

    private void validateConfiguration() {
        List<String> missing = new ArrayList<>();
        if (isBlank(email)) {
            missing.add("BOOTSTRAP_ADMIN_EMAIL");
        }
        if (isBlank(password)) {
            missing.add("BOOTSTRAP_ADMIN_PASSWORD");
        }
        if (isBlank(cellphone)) {
            missing.add("BOOTSTRAP_ADMIN_CELLPHONE");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Bootstrap de administrador incompleto, faltan: " + String.join(", ", missing));
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+$")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL no tiene un formato de correo válido");
        }
        if (!new PasswordValidator().isValid(password, null)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD no cumple la política de contraseñas "
                    + "(mínimo 8 caracteres, mayúscula, minúscula y carácter especial)");
        }
        if (!new PhoneValidator().isValid(cellphone, null)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_CELLPHONE no tiene un formato de celular válido");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
