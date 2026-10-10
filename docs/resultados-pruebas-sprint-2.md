# Resultados de pruebas — Sprint 2

Documento vivo: una sección por corrida relevante, la más reciente arriba. Lo que **no** se probó está dicho de forma explícita.

## Corrida del 2026-10-07 — bugs de Calidad #8–#11 y MFA mínimo

### Entorno y comando

| | |
|---|---|
| Máquina | Windows 11, JDK **17.0.20** (con JDK 24 Lombok no genera código), Maven Wrapper |
| Docker | Docker Desktop, Engine **29.7.2** (API 1.55) |
| Testcontainers | **1.21.4** (antes 1.21.3; ver hallazgo 1) con `postgres:16-alpine` |
| Comando | `./mvnw -B clean test jacoco:report` (en `reservas-backend/`) |
| Solo unitarias (sin Docker) | `./mvnw -B test -Dtest='!*IntegrationTest,!ReservasBackendApplicationTests'` |

### Resultado

**258 pruebas, 0 fallos, 0 errores, 0 omitidas — BUILD SUCCESS en 48 s.** (Línea base de la mañana: 104 pruebas ejecutadas; 101 unitarias pasaban y las 3 clases de integración no arrancaban.)

| | Pruebas | Cobertura de líneas | Cobertura de ramas |
|---|---:|---:|---:|
| Solo unitarias | 195 (antes 101) | **84,6 %** (587/694) — línea base 77,1 % | 85,7 % |
| Unitarias + integración | 258 | **95,2 %** (661/694) | 88,8 % |

Referencia: el Quality Gate del Sprint 2 pide cobertura ≥ 65 %. Las clases nuevas o tocadas quedaron en 91–100 % (`AttemptLimiter`, `AuthAttemptLimiter`, `StepUpServiceImpl`, `MfaEnrollmentFilter`, `AuthServiceImpl`, `MfaController` al 100 %; `GlobalExceptionHandler` 97 %; `TotpService` 91 %). Sin cubrir: `RestAccessDeniedHandler` (0 %, anterior a este trabajo) y `ReservasBackendApplication` (el `main`).

Pruebas de integración (63): `MfaFlowIntegrationTest` (14), `ApiErrorHandlingIntegrationTest` (19), `AuthAndAccessControlIntegrationTest` (12), `UserRegistrationIntegrationTest` (8), `RegistrationRateLimitIntegrationTest` (3), `RegistrationConcurrencyIntegrationTest` (2), `AuditPersistenceIntegrationTest` (4) y `ReservasBackendApplicationTests` (1). Todas extienden `AbstractIntegrationTest` (un solo PostgreSQL por corrida).

### Hallazgos al correr las pruebas de integración por primera vez

1. **Testcontainers 1.21.3 no funciona con Docker Engine 29.** Fallaba con "Could not find a valid Docker environment" aunque Docker estuviera encendido. Es la causa real del problema que `resultados-pruebas-sprint-1.md` §5 atribuyó al pipe `npipe` de Windows: docker-java de esa versión habla la API 1.32 y el motor nuevo la rechaza (HTTP 400). Se resuelve con Testcontainers **1.21.4**.
2. **Las pruebas de integración de Sprint 1 no habían podido correr desde Spring Boot 4:** inyectaban el `ObjectMapper` de Jackson 2 y Boot 4 usa Jackson 3 (`tools.jackson`). Corregido el import.
3. **Aun corriendo, 5 de las 12 de `AuthAndAccessControlIntegrationTest` fallaban** (login 401, proveedor sin `providerId`): registraban más de 5 cuentas desde `127.0.0.1` y el límite de registro las bloqueaba. El límite ahora es configurable (`security.rate-limit.*`) y el perfil de pruebas lo relaja; su comportamiento real se prueba aparte (`RegistrationRateLimitIntegrationTest`).
4. **Los rechazos que el servicio audita (`REJECTED`) se perdían** por el rollback de la transacción. No lo había reportado Calidad. Medido: 3 de las 4 pruebas de `AuditPersistenceIntegrationTest` fallaban; con `noRollbackFor` pasan las 4.
5. Una aserción de `expiresIn == 3600` nunca había corrido y es intermitente (llega 3599 por el redondeo); ahora acepta 3590–3600.

### Regresión de los issues de Calidad (antes → después, medido)

| Issue | Antes | Después |
|---|---|---|
| **#8** el 6.º intento crea la cuenta | Con la clase original de `git HEAD`: intentos 1–6 → "cuenta creada", 7 y 8 → 429 | El 6.º → 429 y no crea la cuenta (unitarias, integración y app real); con 20 hilos simultáneos pasan exactamente 5 |
| **#9** sin límites de longitud | Cadenas de 300 caracteres llegaban a la BD / BCrypt | 400 con los campos señalados; límite exacto aceptado y +1 rechazado en los 3 DTO; 72 bytes para la contraseña |
| **#10** concurrencia → 500 | Sin el manejador: ronda 1 → `[500, 201]` (ambas pruebas fallaban) | `[201, 409]` en las 5 rondas, con el mensaje correcto para correo y para celular |
| **#11** cuerpo vacío → 500 | Sin los manejadores: 12 de 19 casos daban 500 (cuerpo vacío ×3, JSON roto ×3, 415 ×3, 405, UUID inválido, 404) | 400 / 404 / 405 / 415 con formato `ApiError` |

Para medir el "antes" se desactivaron temporalmente los manejadores (y se restauraron) y se compiló la clase original del limitador desde `git HEAD`; no quedó ningún cambio de esos experimentos.

### Verificación de extremo a extremo (aplicación real, no MockMvc)

Se levantó la aplicación con `spring-boot:run` contra un PostgreSQL 16 temporal en Docker (puerto 55432, ya eliminado), con el administrador creado por `AdminBootstrapRunner`, y se ejecutó un guion HTTP que calcula los códigos con el **script de TOTP de `cierre-pendientes-sprint-1.md` §2.6**. **28 de 28 verificaciones correctas:**

- Admin del bootstrap sin MFA: login 200 → cualquier ruta fuera de `/auth/mfa/**` da `403 MFA_ENROLLMENT_REQUIRED` → `setup` 200 → `activate` con código malo 400 → con el código del script 204 → la misma sesión ya opera.
- Login en dos pasos: sin código `401 MFA_REQUIRED` (sin token), código malo `401` genérico, código bueno `200`.
- `PATCH /users/{id}/role` y `DELETE /users/{id}`: sin `X-MFA-Code` `401 MFA_REQUIRED`; código malo `401`; correcto `200`/`204`.
- Issue #11 (cuerpo vacío ×3 endpoints, JSON roto, `/users/abc`, ruta inexistente, `PUT` → 405 con `Allow`, `text/plain` → 415), issue #9 (campos de 300 caracteres), issue #8 (intentos de registro 2–5 → 201 y el 6.º → 429) y fuerza bruta (5 códigos malos y luego 429 incluso con el código correcto).

### Casos de la matriz de MFA

Ver `docs/sprint-2/cierre-pendientes-sprint-1.md` §2.7: automatizados TC-MFA-01–04, 06–19, 21, 22, 26 (y 23 en parte); **pendientes** por no estar implementados: 05/20 (anti-replay), 24/25 (reinicio por otro admin) y el cifrado del secreto.

### Lo que NO se probó

- **Aplicación autenticadora real** (Google/Microsoft Authenticator, TC-MFA-27): solo el script de TOTP, que implementa el mismo estándar.
- **Entorno desplegado en Render** (TC-MFA-28) y **colección Postman**: no se desplegó nada; los cambios están en el árbol de trabajo sin commit.
- **IP real detrás del proxy de Render:** `getRemoteAddr()` puede ser la IP del proxy; el efecto sobre los límites por IP no se pudo verificar fuera de Render (ítem `OWASP-03`).
- **Logs de aplicación sin códigos ni secretos:** solo se verificó la auditoría automáticamente; en el código ningún `log.*` recibe el código ni el secreto, pero no hay una prueba que lo exija.
- Rendimiento, `GET /users/{id}` con carga, y el CI de GitHub (el `build.yml` de la carpeta `reservas-backend/.github` todavía no está en la ubicación que GitHub lee).

### Cómo repetirlo

```bash
cd reservas-backend
export JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"   # JDK 17
./mvnw -B clean test jacoco:report     # Docker encendido; reporte en target/site/jacoco/index.html
```

---

## Prueba del servicio desplegado en Render — 2026-10-10

Se ejecutó la colección de Postman ([`docs/postman/`](postman/README.md)) con Newman contra `https://proyecto2026-2-5zoo.onrender.com`, ya desplegados los commits hasta `Pruebas postman` (pipeline #21 en verde; incluye HU-08 a HU-28 menos las de Tier 3).

| Pasada | Carpetas | Peticiones | Aserciones | Fallos |
|---|---|---:|---:|---:|
| 1 | 00 a 07 (registro, negocio, accesos ajenos, catálogo, disponibilidad pública, reservas, cancelaciones, recursos) | 78 | 106 | **0** |
| 2 | 08 (administrador con MFA: login sin y con TOTP, HU-05/28, eliminación de cuentas y limpieza) | 10 | 13 | **0** |
| **Total** | | **88** | **119** | **0** |

Qué confirma frente a lo desplegado: las **12 migraciones** (incluida la extensión `btree_gist` y la restricción anti-overbooking de `bookings`) se aplicaron en la base de Render; el anti-overbooking, la cancelación por proveedor, `CONFIRMATION_REQUIRED` al desactivar recursos y la cancelación por eliminación de cuenta funcionan allí igual que en local; el administrador de la demo **ya tiene MFA activo** (login sin código → `401 MFA_REQUIRED`) y las operaciones sensibles exigen el código.

Detalles de la ejecución:
- La pasada 1 corrió en ~55 s (respuesta media 627 ms; la más lenta, 4,8 s). El servicio ya estaba despierto.
- Como Newman no conserva las variables de colección entre ejecuciones, el estado de la pasada 1 se reconstruyó desde el servicio (login con los usuarios de esa corrida) y sobre él se corrió la carpeta 08 con un código TOTP manual (`adminMfaCode`). En una corrida completa de una sola vez (Postman o Newman con el administrador configurado) no hace falta.
- **Datos de prueba:** los usuarios `qa.*.ha8ac77x@example.com` (cliente y dos proveedores), con su negocio, servicio, recurso y reservas, fueron **eliminados** por la propia carpeta 08. La base de Render quedó sin restos de la prueba (las reservas pasan a historial solo si hay un cliente o proveedor reales).
- **No se verificó:** carga y rendimiento, lo que depende de enviar correos o notificaciones (fuera de alcance), el comportamiento tras 15 minutos de inactividad (arranque en frío) y el límite de intentos de login/MFA en Render (no se forzó para no bloquear al administrador).

### Hallazgo de seguridad
**La contraseña del administrador de la demo en Render sigue siendo la que está publicada en el repositorio público** (`docs/guia-prueba-aplicacion-desplegada.md`). Es el pendiente `SEC-01`/`SEC-02`: debe rotarse y quitarse de la guía. Mitiga el MFA obligatorio (quien conozca la contraseña aún necesita el código), pero no debería quedar así para la entrega.
