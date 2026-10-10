# Referencia de la API — Sprint 2 (generada desde el código)

> **Generado automáticamente** por `docs/api/generar_referencia.py` leyendo los controladores, `SecurityConfig` y los DTO. **No editar a mano**: ejecuta `python docs/api/generar_referencia.py`. Los contratos narrados, con ejemplos y reglas de negocio, están en [endpoints-sprint-2.md](endpoints-sprint-2.md); los códigos de error, en [errores-api-sprint-2.md](errores-api-sprint-2.md); la guía para verlos en Swagger, en [guia-swagger-openapi.md](guia-swagger-openapi.md).

## 1. Matriz de endpoints y control de acceso

**33 endpoints.** Resumen: 15 Rol PROVEEDOR; 8 Autenticado (cualquier rol); 4 Público; 4 Rol CLIENTE; 2 Rol ADMINISTRADOR. Toda ruta que no es pública exige un token JWT válido con sesión vigente (si no, `401`); un rol insuficiente da `403`. Además de la matriz, los endpoints de gestión comprueban la **pertenencia** (el recurso es del proveedor/cliente que llama) en la capa de servicio (`BusinessAccessService`): ver [OWASP A01](../seguridad/owasp-top10-sprint-2.md).

| Método | Ruta | Acceso | HU | Controlador |
|---|---|---|---|---|
| POST | `/api/v1/auth/login` | **Público** | HU-02 | AuthController |
| POST | `/api/v1/auth/logout` | Autenticado (cualquier rol) | HU-04 | AuthController |
| POST | `/api/v1/auth/mfa/activate` | Autenticado (cualquier rol) | HU-02/05 (ADR-004) | MfaController |
| POST | `/api/v1/auth/mfa/setup` | Autenticado (cualquier rol) | HU-02/05 (ADR-004) | MfaController |
| POST | `/api/v1/bookings` | Rol CLIENTE | HU-22 | BookingController |
| GET | `/api/v1/bookings/me` | Rol CLIENTE | HU-23 | MyBookingsController |
| POST | `/api/v1/bookings/{bookingId}/cancellation` | Rol CLIENTE | HU-25 | BookingCancellationController |
| POST | `/api/v1/bookings/{bookingId}/provider-cancellation` | Rol PROVEEDOR | HU-26 | ProviderBookingCancellationController |
| GET | `/api/v1/businesses` | Autenticado (cualquier rol) | HU-13 | CatalogController |
| GET | `/api/v1/businesses/{businessId}` | Autenticado (cualquier rol) | HU-13 | CatalogController |
| GET | `/api/v1/businesses/{businessId}/booking-lead-time` | Rol PROVEEDOR | HU-08 | BusinessSettingsController |
| PUT | `/api/v1/businesses/{businessId}/booking-lead-time` | Rol PROVEEDOR | HU-08 | BusinessSettingsController |
| GET | `/api/v1/businesses/{businessId}/bookings` | Rol PROVEEDOR | HU-24 | BusinessBookingsController |
| GET | `/api/v1/businesses/{businessId}/resources` | Rol PROVEEDOR | HU-14 | ResourceController |
| POST | `/api/v1/businesses/{businessId}/resources` | Rol PROVEEDOR | HU-14 | ResourceController |
| GET | `/api/v1/businesses/{businessId}/services` | Rol PROVEEDOR | HU-09 | ServiceController |
| POST | `/api/v1/businesses/{businessId}/services` | Rol PROVEEDOR | HU-09 | ServiceController |
| POST | `/api/v1/providers` | **Público** | HU-03 | ProviderController |
| GET | `/api/v1/providers/me` | Autenticado (cualquier rol) | HU-06 | ProviderController |
| GET | `/api/v1/providers/{providerId}` | Autenticado (cualquier rol) | HU-06 | ProviderController |
| GET | `/api/v1/resources/{resourceId}/availability` | Rol PROVEEDOR | HU-19 | ResourceAvailabilityController |
| PUT | `/api/v1/resources/{resourceId}/availability` | Rol PROVEEDOR | HU-19 | ResourceAvailabilityController |
| PUT | `/api/v1/resources/{resourceId}/availability/{dayOfWeek}` | Rol PROVEEDOR | HU-19 | ResourceAvailabilityController |
| POST | `/api/v1/resources/{resourceId}/deactivation` | Rol PROVEEDOR | HU-16 | ResourceDeactivationController |
| POST | `/api/v1/resources/{resourceId}/reactivation` | Rol PROVEEDOR | HU-17 | ResourceReactivationController |
| GET | `/api/v1/services/{serviceId}/availability` | **Público** | HU-20 | ServiceAvailabilityController |
| GET | `/api/v1/services/{serviceId}/resources` | Rol PROVEEDOR | HU-18 | ServiceResourceController |
| PUT | `/api/v1/services/{serviceId}/resources` | Rol PROVEEDOR | HU-18 | ServiceResourceController |
| POST | `/api/v1/users` | **Público** | HU-01 | UserController |
| DELETE | `/api/v1/users/{userId}` | Rol ADMINISTRADOR | HU-05/06 | UserController |
| GET | `/api/v1/users/{userId}` | Autenticado (cualquier rol) | HU-05/06 | UserController |
| GET | `/api/v1/users/{userId}/bookings` | Rol CLIENTE | HU-23 | MyBookingsController |
| PATCH | `/api/v1/users/{userId}/role` | Rol ADMINISTRADOR | HU-05/06 | UserController |

Además, `GET /actuator/health` es público (usado por Render y por el pipeline); los demás endpoints de Actuator no están expuestos.

## 2. Catálogo de DTO

Tipos: `UUID` se serializa como texto; `Instant` como fecha-hora ISO-8601 en UTC; las horas y fechas de negocio viajan como texto (`yyyy-MM-dd`, `HH:mm`) en hora de Bogotá. «obligatorio» = `@NotNull`/`@NotBlank`/`@NotEmpty`. Un error de validación responde `400 VALIDATION_ERROR` con un mapa `fields` (campo → mensaje).

### Identidad y acceso (módulo `identity`)

**`ChangeUserRoleRequest`** — `ChangeUserRoleRequest.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `role` | `String` | obligatorio; tamaño ≤ 30 | El rol es obligatorio · El rol no es válido |

**`ChangeUserRoleResponse`** — `ChangeUserRoleResponse.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `userId` | `UUID` | — | — |
| `email` | `String` | — | — |
| `role` | `String` | — | — |

**`LoginRequest`** — `LoginRequest.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `email` | `String` | obligatorio; tamaño ≤ 150 | El correo electrónico es obligatorio · El correo electrónico no puede superar los 150 caracteres |
| `password` | `String` | obligatorio; tamaño ≤ 72 | La contraseña es obligatoria · La contraseña no puede superar los 72 caracteres |
| `mfaCode` | `String` | tamaño ≤ 10 | El código de verificación no es válido |

**`LoginResponse`** — `LoginResponse.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `token` | `String` | — | — |
| `expiresIn` | `long` | — | — |
| `role` | `String` | — | — |

**`MfaActivateRequest`** — `MfaActivateRequest.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `code` | `String` | obligatorio; tamaño ≤ 10 | El código de verificación es obligatorio · El código de verificación no es válido |

**`MfaSetupResponse`** — `MfaSetupResponse.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `otpauthUri` | `String` | — | — |
| `alreadyEnabled` | `boolean` | — | — |

**`RegisterUserRequest`** — `RegisterUserRequest.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `fullName` | `String` | obligatorio; tamaño ≤ 150 | El nombre completo es obligatorio · El nombre completo no puede superar los 150 caracteres |
| `email` | `String` | obligatorio; correo válido; tamaño ≤ 150 | El correo electrónico es obligatorio · El formato del correo electrónico no es válido · El correo electrónico no puede superar los 150 caracteres |
| `cellphone` | `String` | obligatorio; celular colombiano `3XXXXXXXXX` | El número de celular es obligatorio |
| `password` | `String` | obligatorio; política de contraseña (8+ caracteres, mayúscula, minúscula, carácter especial; ≤ 72 bytes); tamaño ≤ 72 | La contraseña es obligatoria · La contraseña no puede superar los 72 caracteres |

**`RegisterUserResponse`** — `RegisterUserResponse.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `fullName` | `String` | — | — |
| `email` | `String` | — | — |
| `role` | `String` | — | — |

**`UserResponse`** — `UserResponse.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `fullName` | `String` | — | — |
| `email` | `String` | — | — |
| `cellphone` | `String` | — | — |
| `role` | `String` | — | — |
| `enabled` | `boolean` | — | — |
| `createdAt` | `Instant` | — | — |

### Proveedores y negocio (módulo `provider`)

**`BookingLeadTimeRequest`** — `BookingLeadTimeRequest.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `hours` | `Integer` | obligatorio; ≥ 1; ≤ 720 | La antelación mínima es obligatoria · La antelación mínima debe ser un número entero de horas mayor o igual a 1 · La antelación mínima no puede superar 720 horas (30 días) |

**`BookingLeadTimeResponse`** — `BookingLeadTimeResponse.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `businessId` | `UUID` | — | — |
| `hours` | `int` | — | — |

**`ProviderResponse`** — `ProviderResponse.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `providerId` | `UUID` | — | — |
| `userId` | `UUID` | — | — |
| `businesses` | `List<BusinessSummary>` | — | — |

**`BusinessSummary`** — `ProviderResponse.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `name` | `String` | — | — |

**`RegisterProviderRequest`** — `RegisterProviderRequest.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `fullName` | `String` | obligatorio; tamaño ≤ 150 | El nombre completo es obligatorio · El nombre completo no puede superar los 150 caracteres |
| `email` | `String` | obligatorio; correo válido; tamaño ≤ 150 | El correo electrónico es obligatorio · El formato del correo electrónico no es válido · El correo electrónico no puede superar los 150 caracteres |
| `cellphone` | `String` | obligatorio; celular colombiano `3XXXXXXXXX` | El número de celular es obligatorio |
| `password` | `String` | obligatorio; política de contraseña (8+ caracteres, mayúscula, minúscula, carácter especial; ≤ 72 bytes); tamaño ≤ 72 | La contraseña es obligatoria · La contraseña no puede superar los 72 caracteres |
| `businessName` | `String` | obligatorio; tamaño ≤ 150 | El nombre del negocio es obligatorio · El nombre del negocio no puede superar los 150 caracteres |

**`RegisterProviderResponse`** — `RegisterProviderResponse.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `userId` | `UUID` | — | — |
| `fullName` | `String` | — | — |
| `email` | `String` | — | — |
| `role` | `String` | — | — |
| `providerId` | `UUID` | — | — |
| `businessId` | `UUID` | — | — |
| `businessName` | `String` | — | — |

### Servicios y catálogo (módulo `service`)

**`BusinessItem`** — `CatalogDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `name` | `String` | — | — |

**`ServiceItem`** — `CatalogDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `name` | `String` | — | — |
| `description` | `String` | — | — |
| `durationMinutes` | `int` | — | — |
| `priceCop` | `long` | — | — |

**`BusinessPageResponse`** — `CatalogDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `items` | `List<BusinessItem>` | — | — |
| `page` | `int` | — | — |
| `size` | `int` | — | — |
| `totalElements` | `long` | — | — |
| `totalPages` | `int` | — | — |
| `message` | `String` | — | — |

**`BusinessDetailResponse`** — `CatalogDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `name` | `String` | — | — |
| `services` | `List<ServiceItem>` | — | — |
| `message` | `String` | — | — |

**`CreateServiceRequest`** — `CreateServiceRequest.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `name` | `String` | obligatorio; tamaño ≤ 150 | El nombre del servicio es obligatorio · El nombre del servicio no puede superar los 150 caracteres |
| `description` | `String` | tamaño ≤ 500 | La descripción no puede superar los 500 caracteres |
| `durationMinutes` | `Integer` | obligatorio; ≥ 1; ≤ 1440 | La duración es obligatoria · La duración debe ser un número entero de minutos mayor que 0 · La duración no puede superar 1440 minutos (24 horas) |
| `priceCop` | `Long` | obligatorio; ≥ 0; ≤ 1000000000 | El precio es obligatorio · El precio no puede ser negativo · El precio no puede superar 1.000.000.000 COP |

**`SlotResource`** — `ServiceAvailabilityDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `name` | `String` | — | — |

**`Slot`** — `ServiceAvailabilityDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `start` | `String` | — | — |
| `end` | `String` | — | — |
| `resources` | `List<SlotResource>` | — | — |

**`ServiceAvailabilityResponse`** — `ServiceAvailabilityDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `serviceId` | `UUID` | — | — |
| `serviceName` | `String` | — | — |
| `date` | `String` | — | — |
| `timezone` | `String` | — | — |
| `durationMinutes` | `int` | — | — |
| `slots` | `List<Slot>` | — | — |
| `message` | `String` | — | — |

**`ServiceResourcesDtos`** — `ServiceResourcesDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `resourceIds` | `List<UUID>` | obligatorio; tamaño ≤ 100; elementos: obligatorio | La lista de recursos es obligatoria (puede ir vacía) · No se pueden asignar más de 100 recursos a un servicio · Los ids de recursos no pueden ser nulos |

**`AssignResourcesRequest`** — `ServiceResourcesDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `resourceIds` | `List<UUID>` | obligatorio; tamaño ≤ 100; elementos: obligatorio | La lista de recursos es obligatoria (puede ir vacía) · No se pueden asignar más de 100 recursos a un servicio · Los ids de recursos no pueden ser nulos |

**`AssignedResource`** — `ServiceResourcesDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `name` | `String` | — | — |
| `type` | `String` | — | — |
| `active` | `boolean` | — | — |

**`ServiceResourcesResponse`** — `ServiceResourcesDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `serviceId` | `UUID` | — | — |
| `resources` | `List<AssignedResource>` | — | — |

**`ServiceResponse`** — `ServiceResponse.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `businessId` | `UUID` | — | — |
| `name` | `String` | — | — |
| `description` | `String` | — | — |
| `durationMinutes` | `int` | — | — |
| `priceCop` | `long` | — | — |
| `active` | `boolean` | — | — |
| `createdAt` | `Instant` | — | — |

### Recursos y horarios (módulo `resource`)

**`TimeRange`** — `AvailabilityDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `start` | `String` | obligatorio; patrón `^([01]\d\|2[0-3]):[0-5]\d$` | El rango horario no es válido: use el formato HH:mm (00:00 a 23:59) |
| `end` | `String` | obligatorio; patrón `^([01]\d\|2[0-3]):[0-5]\d$` | El rango horario no es válido: use el formato HH:mm (00:00 a 23:59) |

**`DayRangesRequest`** — `AvailabilityDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `ranges` | `List<TimeRange>` | obligatorio; tamaño ≤ 10; elementos: valida el contenido anidado; elementos: obligatorio | La lista de rangos es obligatoria (puede ir vacía) · Un día admite como máximo 10 rangos horarios · Un rango horario no puede ser nulo |

**`DayScheduleRequest`** — `AvailabilityDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `dayOfWeek` | `Integer` | obligatorio; ≥ 1; ≤ 7 | El día de la semana es obligatorio · El día debe estar entre 1 (lunes) y 7 (domingo) |
| `ranges` | `List<TimeRange>` | obligatorio; tamaño ≤ 10; elementos: valida el contenido anidado; elementos: obligatorio | La lista de rangos es obligatoria (puede ir vacía) · Un día admite como máximo 10 rangos horarios · Un rango horario no puede ser nulo |

**`WeekScheduleRequest`** — `AvailabilityDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `days` | `List<DayScheduleRequest>` | obligatorio; tamaño ≤ 7; elementos: valida el contenido anidado; elementos: obligatorio | La lista de días es obligatoria (puede ir vacía) · La semana tiene como máximo 7 días · Un día no puede ser nulo |

**`DaySchedule`** — `AvailabilityDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `dayOfWeek` | `int` | — | — |
| `ranges` | `List<TimeRange>` | — | — |

**`AvailabilityResponse`** — `AvailabilityDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `resourceId` | `UUID` | — | — |
| `timezone` | `String` | — | — |
| `days` | `List<DaySchedule>` | — | — |

**`CreateResourceRequest`** — `CreateResourceRequest.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `name` | `String` | obligatorio; tamaño ≤ 150 | El nombre del recurso es obligatorio · El nombre del recurso no puede superar los 150 caracteres |
| `type` | `String` | obligatorio; patrón `(?i)\s*\|SALA\|EQUIPO\|PERSONAL` | El tipo del recurso es obligatorio · El tipo debe ser SALA, EQUIPO o PERSONAL |

**`ResourceResponse`** — `ResourceResponse.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `businessId` | `UUID` | — | — |
| `name` | `String` | — | — |
| `type` | `String` | — | — |
| `active` | `boolean` | — | — |
| `createdAt` | `Instant` | — | — |

**`ResourceStatusResponse`** — `ResourceStatusResponse.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `resourceId` | `UUID` | — | — |
| `name` | `String` | — | — |
| `active` | `boolean` | — | — |

### Reservas (módulo `reservation`)

**`BookingDtos`** — `BookingDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `serviceId` | `UUID` | obligatorio | El servicio es obligatorio |
| `date` | `String` | obligatorio | La fecha es obligatoria |
| `startTime` | `String` | obligatorio; patrón `^([01]\d\|2[0-3]):[0-5]\d$` | La hora de inicio es obligatoria · La hora debe tener el formato HH:mm (00:00 a 23:59) |
| `endTime` | `String` | obligatorio; patrón `^([01]\d\|2[0-3]):[0-5]\d$` | La hora de fin es obligatoria · La hora debe tener el formato HH:mm (00:00 a 23:59) |
| `resourceId` | `UUID` | — | — |
| `reason` | `String` | tamaño ≤ 500 | El motivo no puede superar los 500 caracteres |
| `reason` | `String` | obligatorio; tamaño ≤ 500 | El motivo de la cancelación es obligatorio · El motivo no puede superar los 500 caracteres |

**`CreateBookingRequest`** — `BookingDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `serviceId` | `UUID` | obligatorio | El servicio es obligatorio |
| `date` | `String` | obligatorio | La fecha es obligatoria |
| `startTime` | `String` | obligatorio; patrón `^([01]\d\|2[0-3]):[0-5]\d$` | La hora de inicio es obligatoria · La hora debe tener el formato HH:mm (00:00 a 23:59) |
| `endTime` | `String` | obligatorio; patrón `^([01]\d\|2[0-3]):[0-5]\d$` | La hora de fin es obligatoria · La hora debe tener el formato HH:mm (00:00 a 23:59) |
| `resourceId` | `UUID` | — | — |

**`BookingItem`** — `BookingDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `status` | `String` | — | — |
| `serviceId` | `UUID` | — | — |
| `serviceName` | `String` | — | — |
| `businessId` | `UUID` | — | — |
| `businessName` | `String` | — | — |
| `resourceId` | `UUID` | — | — |
| `resourceName` | `String` | — | — |
| `date` | `String` | — | — |
| `startTime` | `String` | — | — |
| `endTime` | `String` | — | — |
| `priceCop` | `long` | — | — |
| `cancelOrigin` | `String` | — | — |
| `cancelReason` | `String` | — | — |
| `cancelledAt` | `Instant` | — | — |
| `createdAt` | `Instant` | — | — |

**`BookingPageResponse`** — `BookingDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `items` | `List<BookingItem>` | — | — |
| `page` | `int` | — | — |
| `size` | `int` | — | — |
| `totalElements` | `long` | — | — |
| `totalPages` | `int` | — | — |
| `message` | `String` | — | — |

**`BusinessBookingItem`** — `BookingDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `status` | `String` | — | — |
| `clientId` | `UUID` | — | — |
| `clientName` | `String` | — | — |
| `clientEmail` | `String` | — | — |
| `serviceId` | `UUID` | — | — |
| `serviceName` | `String` | — | — |
| `resourceId` | `UUID` | — | — |
| `resourceName` | `String` | — | — |
| `date` | `String` | — | — |
| `startTime` | `String` | — | — |
| `endTime` | `String` | — | — |
| `priceCop` | `long` | — | — |
| `cancelOrigin` | `String` | — | — |
| `cancelReason` | `String` | — | — |
| `cancelledAt` | `Instant` | — | — |
| `createdAt` | `Instant` | — | — |

**`BusinessBookingPageResponse`** — `BookingDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `items` | `List<BusinessBookingItem>` | — | — |
| `page` | `int` | — | — |
| `size` | `int` | — | — |
| `totalElements` | `long` | — | — |
| `totalPages` | `int` | — | — |
| `message` | `String` | — | — |

**`CancelBookingRequest`** — `BookingDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `reason` | `String` | tamaño ≤ 500 | El motivo no puede superar los 500 caracteres |

**`ProviderCancelRequest`** — `BookingDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `reason` | `String` | obligatorio; tamaño ≤ 500 | El motivo de la cancelación es obligatorio · El motivo no puede superar los 500 caracteres |

**`BookingResponse`** — `BookingDtos.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `id` | `UUID` | — | — |
| `status` | `String` | — | — |
| `serviceId` | `UUID` | — | — |
| `serviceName` | `String` | — | — |
| `businessId` | `UUID` | — | — |
| `businessName` | `String` | — | — |
| `resourceId` | `UUID` | — | — |
| `resourceName` | `String` | — | — |
| `date` | `String` | — | — |
| `startTime` | `String` | — | — |
| `endTime` | `String` | — | — |
| `priceCop` | `long` | — | — |
| `createdAt` | `Instant` | — | — |

**`ResourceDeactivationController`** — `ResourceDeactivationController.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `deactivationService` | `ResourceDeactivationService` | — | — |
| `identityService` | `IdentityService` | — | — |
| `confirm` | `boolean` | — | — |

**`DeactivationRequest`** — `ResourceDeactivationController.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `confirm` | `boolean` | — | — |

**`DeactivationResult`** — `ResourceDeactivationService.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `resourceId` | `UUID` | — | — |
| `name` | `String` | — | — |
| `active` | `boolean` | — | — |
| `cancelledBookings` | `int` | — | — |

### Errores (común a todos los módulos) (módulo `common`)

**`ApiError`** — `ApiError.java`

| Campo | Tipo | Restricciones | Mensaje de error |
|---|---|---|---|
| `timestamp` | `LocalDateTime` | — | — |
| `status` | `int` | — | — |
| `error` | `String` | — | — |
| `message` | `String` | — | — |
| `path` | `String` | — | — |
| `fields` | `Map<String, String>` | — | — |
| `traceId` | `String` | — | — |

_Total: 50 DTO._
