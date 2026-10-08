package com.codefactory.reservas_backend.identity.controller.dto;

import com.codefactory.reservas_backend.provider.controller.dto.RegisterProviderRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresión del issue de Calidad #9 ("sin validación de longitud en nombre,
 * contraseña y correo"): límite exacto aceptado y límite + 1 rechazado, por
 * campo, en los tres DTO de entrada (registro de cliente, registro de
 * proveedor y login). Los topes salen de las columnas de la BD
 * (VARCHAR(150)) y de BCrypt (72 bytes).
 */
class RequestLengthValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    /**
     * Correo de formato válido con la longitud total pedida. @Email limita la
     * parte local a 64 caracteres y cada etiqueta del dominio a 63, así que se
     * arma con una parte local de 64, una primera etiqueta de 63 y una segunda
     * que completa el largo (válida hasta ~150 caracteres).
     */
    private static String emailOfLength(int total) {
        String local = "a".repeat(64);
        int domainLength = total - local.length() - 1;
        String firstLabel = "b".repeat(63);
        String secondLabel = "c".repeat(domainLength - firstLabel.length() - 1);
        return local + "@" + firstLabel + "." + secondLabel;
    }

    /** Contraseña que cumple la política (mayúscula, minúscula, especial) con la longitud pedida. */
    private static String passwordOfLength(int total) {
        return "Aa#" + "x".repeat(total - 3);
    }

    private static <T> Set<String> invalidFields(T dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private RegisterUserRequest validUser() {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setFullName("Simon Betancur Sosa");
        request.setEmail("simon@example.com");
        request.setCellphone("3001234567");
        request.setPassword("Segura#2026");
        return request;
    }

    private RegisterProviderRequest validProvider() {
        RegisterProviderRequest request = new RegisterProviderRequest();
        request.setFullName("Carlos Gómez");
        request.setEmail("carlos@example.com");
        request.setCellphone("3019876543");
        request.setPassword("Segura#2026");
        request.setBusinessName("Centro Deportivo ABC");
        return request;
    }

    // --- Registro de cliente (HU-01) ---

    @Test
    void elRegistroDeClienteDebeAceptarLosValoresEnElLimiteExacto() {
        RegisterUserRequest request = validUser();
        request.setFullName("n".repeat(150));
        request.setEmail(emailOfLength(150));
        request.setPassword(passwordOfLength(72));

        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void elRegistroDeClienteDebeRechazarNombreCorreoYContrasenaConUnCaracterDeMas() {
        RegisterUserRequest request = validUser();
        request.setFullName("n".repeat(151));
        request.setEmail(emailOfLength(151));
        request.setPassword(passwordOfLength(73));

        assertThat(invalidFields(request)).containsExactlyInAnyOrder("fullName", "email", "password");
    }

    @Test
    void elRegistroDeClienteDebeRechazarCadenasMuyLargasComoLasDelIssue() {
        RegisterUserRequest request = validUser();
        request.setFullName("n".repeat(300));
        request.setEmail(emailOfLength(300));
        request.setPassword(passwordOfLength(300));

        assertThat(invalidFields(request)).containsExactlyInAnyOrder("fullName", "email", "password");
    }

    @Test
    void laContrasenaSobreElLimiteDeBcryptDebeInformarElMensajeDelCampo() {
        RegisterUserRequest request = validUser();
        request.setPassword(passwordOfLength(73));

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("La contraseña no puede superar los 72 caracteres");
    }

    // --- Registro de proveedor (HU-03) ---

    @Test
    void elRegistroDeProveedorDebeAceptarLosValoresEnElLimiteExacto() {
        RegisterProviderRequest request = validProvider();
        request.setFullName("n".repeat(150));
        request.setEmail(emailOfLength(150));
        request.setPassword(passwordOfLength(72));
        request.setBusinessName("b".repeat(150));

        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void elRegistroDeProveedorDebeRechazarCualquierCampoConUnCaracterDeMas() {
        RegisterProviderRequest request = validProvider();
        request.setFullName("n".repeat(151));
        request.setEmail(emailOfLength(151));
        request.setPassword(passwordOfLength(73));
        request.setBusinessName("b".repeat(151));

        assertThat(invalidFields(request)).containsExactlyInAnyOrder("fullName", "email", "password", "businessName");
    }

    // --- Login (HU-02) ---

    @Test
    void elLoginDebeAceptarLosValoresEnElLimiteExacto() {
        LoginRequest request = new LoginRequest();
        request.setEmail(emailOfLength(150));
        request.setPassword("p".repeat(72));
        request.setMfaCode("1".repeat(10));

        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void elLoginDebeRechazarCorreoContrasenaYCodigoConUnCaracterDeMas() {
        LoginRequest request = new LoginRequest();
        request.setEmail(emailOfLength(151));
        request.setPassword("p".repeat(73));
        request.setMfaCode("1".repeat(11));

        assertThat(invalidFields(request)).containsExactlyInAnyOrder("email", "password", "mfaCode");
    }

    // --- Otros DTO con texto libre ---

    @Test
    void laActivacionDeMfaDebeRechazarUnCodigoDemasiadoLargo() {
        MfaActivateRequest request = new MfaActivateRequest();
        request.setCode("1".repeat(11));

        assertThat(invalidFields(request)).containsExactly("code");
    }

    @Test
    void elCambioDeRolDebeRechazarUnRolDemasiadoLargo() {
        ChangeUserRoleRequest request = new ChangeUserRoleRequest();
        request.setRole("R".repeat(31));

        assertThat(invalidFields(request)).containsExactly("role");
    }
}
