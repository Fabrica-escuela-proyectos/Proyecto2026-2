# Verificación de criterios de aceptación — Sprint 1

Cruce escenario por escenario de los 6 Gherkin reales (`docs/HU-*.txt`) contra el código de producción de `reservas-backend/`. Fecha de corte: 2026-09-22. A diferencia de `estado-proyecto-sprint-1.md` (que resume qué se construyó), este documento verifica **cada condición puntual** de cada `Then` contra el código, con archivo y línea como evidencia.

**Resultado global: 34 de 39 criterios implementados. 2 no implementados, 3 parciales.**

| HU | Criterios | Implementados | Parciales | No implementados |
|---|---|---|---|---|
| HU-01 Registrar cliente | 9 | 8 | 1 | 0 |
| HU-02 Inicio de sesión | 8 | 6 | 0 | 2 |
| HU-03 Registro de proveedor | 7 | 7 | 0 | 0 |
| HU-04 Cerrar sesión | 3 | 2 | 1 | 0 |
| HU-05 Gestionar roles y permisos | 5 | 4 | 1 | 0 |
| HU-06 Acceso según rol | 7 | 7 | 0 | 0 |

Los tests citados están en `reservas-backend/src/test/java/com/codefactory/reservas_backend/`; las rutas de código en `reservas-backend/src/main/java/com/codefactory/reservas_backend/`.

## HU-01 — Registrar cliente

1. **Registro exitoso (cuenta, rol Cliente, hash, estado autenticado/redirect) — PARCIAL.** `identity/application/UserRegistrationService.java:71-93` asigna el rol CLIENTE desde el servidor y hashea la contraseña (línea 83). `UserController.java:45-53` responde `201` con `RegisterUserResponse`, **sin token ni sesión**: el registro no deja al usuario autenticado, solo cumple la rama "redirige a login" del criterio (que es un OR en el Gherkin). No está documentado como decisión de diseño.
2. **Correo ya existente → rechaza — IMPLEMENTADO.** `UserRegistrationService.java:54-58` (`DuplicateEmailException`) → 409 en `common/error/GlobalExceptionHandler.java:64-67`.
3. **Correo con formato inválido — IMPLEMENTADO.** `RegisterUserRequest.java:24-26` (`@Email`).
4. **Celular con formato inválido (abcde12345, 12345, 123) — IMPLEMENTADO.** `common/validation/PhoneValidator.java:23,26-31` (`^3\d{9}$`). Test con esos 3 valores exactos: `PhoneValidatorTest.java:30-40`.
5. **Celular ya registrado en otra cuenta — IMPLEMENTADO.** `UserRegistrationService.java:61-65` (`DuplicatePhoneException`) → 409.
6. **Contraseña que no cumple política (¡ * + °) — IMPLEMENTADO.** `common/validation/PasswordValidator.java:20-31`: min. 8, mayúscula, minúscula, cualquier carácter no alfanumérico (cubre los 4 ejemplos). Test parametrizado con esos 4 caracteres: `PasswordValidatorTest.java:30-42`.
7. **Campo obligatorio faltante (por campo) — IMPLEMENTADO.** Mensajes específicos por campo en `RegisterUserRequest.java:21,24,28,32`, mapa `fields` en `GlobalExceptionHandler.java:47-52`.
8. **Bloqueo temporal por múltiples intentos desde el mismo origen — IMPLEMENTADO.** `identity/infrastructure/RegistrationRateLimiter.java:33-66` (5 intentos/10 min, bloqueo 15 min). *Caveat:* usa `HttpServletRequest.getRemoteAddr()`, sin considerar `X-Forwarded-For` — detrás de un proxy/balanceador todas las peticiones compartirían la misma IP aparente.
9. **Contraseña y datos sensibles protegidos, ni el equipo técnico puede verlos — IMPLEMENTADO.** Solo se guarda `passwordHash` (BCrypt, irreversible) en `identity/domain/User.java:56-57`; `UserResponse` nunca lo expone; `AuditLog` no tiene ningún campo para contraseñas.

## HU-02 — Inicio de sesión

10. **Login exitoso, sesión con duración determinada — IMPLEMENTADO.** `identity/application/AuthServiceImpl.java:48-93`: valida credenciales, emite JWT de 1h (`JwtTokenProvider.java:37`) y persiste `Session`.
11. **Credenciales inválidas o incompletas → rechaza — IMPLEMENTADO.** Mismo `InvalidCredentialsException` sin revelar cuál dato falló (`AuthServiceImpl.java:49-61`).
12. **Token protegido, no expuesto — IMPLEMENTADO.** Solo se persiste el `jti` (`identity/domain/Session.java:40-44`), nunca el JWT completo; viaja solo en el header `Authorization`.
13. **Expiración a 1 hora — IMPLEMENTADO.** `JwtTokenProvider.java:37` + `Session.isValid` (`Session.java:61-63`) + rechazo en `JwtAuthenticationFilter.java:82-97`.
14. **Logs de login sin contraseña ni datos sensibles — IMPLEMENTADO.** `AuthServiceImpl.java:59,73,89` nunca audita el password ni el JWT.
15. **Identifica el rol y limita funcionalidades a ese rol — IMPLEMENTADO.** `AuthServiceImpl.java:63,92` + authorities `ROLE_<rol>` en `JwtAuthenticationFilter.java:100-112`.
16. **Verificación adicional (MFA) para cuentas administrativas — IMPLEMENTADO.** `AuthServiceImpl.java:70-75`: si es ADMINISTRADOR y ya tiene MFA activo, exige el código.
17. **Confirmación adicional para "cambiar mi contraseña" y "modificar permisos de otro usuario" — NO IMPLEMENTADO.** No existe ningún endpoint de cambio de contraseña propia en todo el repo. `PATCH /api/v1/users/{id}/role` (la única operación real de "modificar permisos") solo exige el rol ADMINISTRADOR vía JWT — ningún paso de confirmación adicional antes de ejecutar el cambio.

## HU-03 — Registro de proveedor

18. **Registro exitoso: cuenta + negocio, rol Proveedor controlado, hash — IMPLEMENTADO.** `provider/application/ProviderRegistrationService.java:38-77`.
19. **Correo ya existente → rechaza — IMPLEMENTADO.** `identity/application/UserProvisioningServiceImpl.java:28-30`.
20. **Correo con formato inválido — IMPLEMENTADO.** `provider/controller/dto/RegisterProviderRequest.java:27-28`.
21. **Contraseña que no cumple política — IMPLEMENTADO.** Misma validación de HU-01 (`RegisterProviderRequest.java:35-36`).
22. **Campo obligatorio faltante, incluyendo "nombre del negocio" — IMPLEMENTADO.** `RegisterProviderRequest.java:24,27,31,35,39`.
23. **Rol distinto enviado por el visitante se ignora — IMPLEMENTADO.** El DTO no tiene ningún campo `role`; se fija server-side en `ProviderRegistrationService.java:54`.
24. **Logs de registro/login de proveedor sin datos sensibles — IMPLEMENTADO.** Mismo mecanismo de auditoría de HU-01/HU-02.

## HU-04 — Cerrar sesión

25. **Cierre exitoso invalida todos los dispositivos, sin renovación automática — IMPLEMENTADO.** `identity/infrastructure/SessionRepository.java:24-29` (`revokeAllByUserId`, todas las sesiones del usuario, no solo la del token actual). No existe ningún endpoint de refresh token en el proyecto.
26. **Sesión con más de 1 hora se trata como revocada — IMPLEMENTADO.** Ver criterio 13.
27. **Error al cerrar sesión / doble logout / logout sin token — PARCIAL.** Sin token → 401 uniforme de Spring Security. Logout repetido con el mismo token → también 401, pero **indistinguible** de "nunca hubo sesión" (no hay un mensaje tipo "la sesión ya estaba cerrada"). El escenario de "falla de conexión con el servidor" no es verificable del lado del backend.

## HU-05 — Gestionar roles y permisos

28. **Admin asigna rol Administrador a un Cliente → actualiza rol y evento MFA obligatorio — IMPLEMENTADO.** `identity/application/UserManagementServiceImpl.java:96-107` dispara `mfaService.triggerMandatorySetup(...)` de forma incondicional al ascender a ADMINISTRADOR.
29. **Admin intenta cambiar rol de un Proveedor → error — IMPLEMENTADO.** `UserManagementServiceImpl.java:68-75` (`ProviderRoleImmutableException`) → 403.
30. **Admin elimina usuario Cliente/Proveedor: se elimina en su totalidad, se guarda historial de auditoría — IMPLEMENTADO.** `UserManagementServiceImpl.java:112-141`; el registro de auditoría no tiene FK hacia `users`, así que sobrevive al borrado. *Nota:* el endpoint también excluye la eliminación de otros Administradores — es una interpretación del equipo (documentada en el código), el Gherkin no la pide ni la prohíbe explícitamente.
31. **Admin intenta asignar rol inexistente → error uniforme — IMPLEMENTADO.** `UserManagementServiceImpl.java:78-85` (`RoleNotFoundException`) → 400.
32. **Cualquier usuario intenta modificar sus propios permisos → error uniforme de auto-modificación — PARCIAL.** La regla existe (`UserManagementServiceImpl.java:58-63`, `SelfModificationException`) pero solo es alcanzable en la práctica para un **Administrador** que se autoataca. Un Cliente/Proveedor nunca llega a esa validación: `@PreAuthorize("hasRole('ADMINISTRADOR')")` en `UserController.java:61` los bloquea antes, con el mensaje genérico de "rol insuficiente" — no con el de "no puede modificarse a sí mismo" que pide el Gherkin literal (que dice "un usuario autenticado con **cualquier** rol"). El resultado (rechazado) es correcto; el mensaje específico no siempre.

## HU-06 — Acceso según rol

33. **Cliente gestiona únicamente sus propios datos — IMPLEMENTADO** (dentro del alcance real de Sprint 1 — "reservas" como funcionalidad no existe todavía; aplica a `GET /api/v1/users/{id}` sobre sí mismo). `UserManagementServiceImpl.java:41-53`.
34. **Cliente intenta gestionar datos de otro usuario → 403 — IMPLEMENTADO.** Mismo método, líneas 45-47.
35. **Proveedor gestiona únicamente los recursos de su propio negocio — IMPLEMENTADO.** `provider/application/ProviderQueryService.java:35-55`.
36. **Proveedor intenta gestionar recursos de OTRO negocio → denegado por pertenencia, no solo por rol — IMPLEMENTADO.** `ProviderQueryService.java:45-53` calcula `isOwner` explícitamente; el comentario documenta a propósito que el chequeo de rol ya se superó y esta es la verificación adicional de pertenencia.
37. **Acceso a funcionalidad autorizada para el rol → permitido — IMPLEMENTADO.** P.ej. Administrador en `PATCH /users/{id}/role`.
38. **Acceso a funcionalidad NO autorizada para el rol → denegado — IMPLEMENTADO.** P.ej. Cliente en la misma ruta → 403.
39. **Sesión expirada/revocada o sin iniciar sesión en operación protegida → 401, exige nueva autenticación — IMPLEMENTADO.** `identity/infrastructure/SecurityConfig.java:61-72` + `JwtAuthenticationFilter.java:82-97` + `RestAuthenticationEntryPoint.java:36-51`.

## Resumen de brechas (para citar en el entregable)

1. **[Falta funcionalidad] Cambiar la propia contraseña** — no existe ningún endpoint. HU-02, escenario "Verificación adicional para operaciones sensibles".
2. **[Falta control] Confirmación adicional al modificar el rol de otro usuario** — `PATCH /users/{id}/role` no exige ningún paso extra más allá del JWT de administrador. Mismo escenario de HU-02.
3. **[Decisión sin documentar] El registro no autentica** — cumple la rama "redirige a login" del criterio OR de HU-01, pero no está señalado como decisión intencional en el código.
4. **[Mensaje no diferenciado] Doble logout / logout sin sesión** — ambos dan el mismo 401 genérico; no hay un mensaje de "la sesión ya estaba cerrada" (HU-04).
5. **[Mensaje no siempre específico] Auto-modificación de rol** — la regla funciona, pero el mensaje "no puede modificarse a sí mismo" solo se ve cuando quien lo intenta ya es Administrador; para Cliente/Proveedor se ve el mensaje genérico de rol insuficiente (HU-05).

Ninguna de estas 5 brechas es un defecto de lógica de negocio incorrecta — son omisiones (1 y 2) o matices de mensaje/documentación (3, 4, 5) frente al texto literal del Gherkin.

## Referencias

- Historias de usuario originales: `docs/HU-01-Registrar-cliente.txt`, `docs/HU 02 - Inicio de sesión.txt`, `docs/HU - 03 Registro de proveedor de se.txt`, `docs/HU 04 - Cerrar sesión.txt`, `docs/HU 05 - Gestionar roles y permisos.txt`, `docs/HU 06 - Acceso segun rol.txt`
- Resumen de qué se construyó (no criterio por criterio): [docs/estado-proyecto-sprint-1.md](estado-proyecto-sprint-1.md)
- Resultados de pruebas automatizadas y verificación manual: [docs/resultados-pruebas-sprint-1.md](resultados-pruebas-sprint-1.md)
