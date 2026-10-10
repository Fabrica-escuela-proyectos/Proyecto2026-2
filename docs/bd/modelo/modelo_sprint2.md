# Modelo de Datos — Sprint 2 (MER, lógico y físico)
Responsable BD: ANDRAUS LOPEZ JUAN SEBASTIAN · Motor: PostgreSQL (Supabase) · Extiende el esquema del Sprint 1

## 1. MER refinado: entidades nuevas

| Entidad | Razón de existir |
|---|---|
| SERVICIO (`services`) | Oferta de un negocio: duración, precio, activo/eliminado (HU servicios) |
| RECURSO (`resources`) | Elemento reservable de un negocio (cancha, sala, profesional) |
| SERVICIO_RECURSO (`service_resources`) | N:M: qué recursos pueden prestar qué servicio |
| HORARIO (`resource_schedules`) | Disponibilidad semanal de un recurso |
| BLOQUEO (`resource_blocks`) | Cierres puntuales (mantenimiento, festivo) que anulan disponibilidad |
| RESERVA (`reservations`) | Reserva de un cliente: servicio + recurso + intervalo + estado; incluye datos de cancelación |

Ajuste al Sprint 1: `sessions.mfa_verified_at` (MFA verificado en la sesión, para operaciones sensibles).

## 2. Relaciones y cardinalidades

| Relación | Cardinalidad |
|---|---|
| NEGOCIO — SERVICIO | 1 a 0..N |
| NEGOCIO — RECURSO | 1 a 0..N |
| SERVICIO — SERVICIO_RECURSO — RECURSO | N:M (mismo negocio, garantizado por FK compuesta) |
| RECURSO — HORARIO | 1 a 0..N |
| RECURSO — BLOQUEO | 1 a 0..N |
| USUARIO (cliente) — RESERVA | 1 a 0..N |
| SERVICIO_RECURSO — RESERVA | 1 a 0..N (la reserva usa una combinación servicio+recurso válida) |
| USUARIO — RESERVA (cancelada por) | 0..1 a 0..N |

```
NEGOCIO (1) ──< SERVICIO >──< SERVICIO_RECURSO >──< RECURSO >── (1) NEGOCIO
RECURSO (1) ──< HORARIO
RECURSO (1) ──< BLOQUEO
USUARIO (1) ──< RESERVA >── (1) SERVICIO_RECURSO
```

## 3. Modelo lógico y físico (tipos PostgreSQL)

**services**: id BIGSERIAL PK · business_id FK→businesses · name VARCHAR(150) NN · description VARCHAR(500) · duration_minutes INT NN CHECK 5–480 · price NUMERIC(12,2) NN CHECK ≥0 · is_active BOOL · deleted_at TIMESTAMPTZ · created_at/updated_at · UNIQUE(id,business_id)

**resources**: id PK · business_id FK→businesses · name NN · description · is_active · deleted_at · created_at/updated_at · UNIQUE(id,business_id)

**service_resources**: PK(service_id,resource_id) · business_id · FK(service_id,business_id)→services · FK(resource_id,business_id)→resources

**resource_schedules**: id PK · resource_id FK · day_of_week SMALLINT CHECK 1–7 (ISO) · start_time, end_time TIME · CHECK end>start · EXCLUDE (sin traslape por recurso y día)

**resource_blocks**: id PK · resource_id FK · starts_at, ends_at TIMESTAMPTZ · CHECK ends>starts · reason · created_by FK→users

**reservations**: id PK · client_id FK→users · (service_id,resource_id) FK→service_resources · starts_at, ends_at TIMESTAMPTZ · status CHECK IN (CONFIRMADA, CANCELADA, COMPLETADA) · cancelled_at, cancelled_by FK→users, cancellation_reason · created_at/updated_at · CHECK end>start · CHECK coherencia de cancelación · EXCLUDE (sin doble reserva del recurso en intervalos traslapados, solo estados ≠ CANCELADA)

## 4. Índices y justificación

| Índice | Consulta que soporta |
|---|---|
| `ex_res_no_overlap` (GiST resource_id, tstzrange) | Anti doble reserva + disponibilidad (C2, C3) |
| `ex_schedule_no_overlap` (GiST) | Horario del recurso por día (C2) |
| `idx_resource_blocks_overlap` (GiST) | Bloqueos que cruzan una franja (C2) |
| `idx_reservations_client` (client_id, starts_at DESC) | Mis reservas (C4) |
| `idx_reservations_service` (service_id, starts_at) | Reservas del negocio por rango (C5) |
| `uq_services_business_name`, `uq_resources_business_name` (únicos parciales, activos) | Nombre único por negocio, permite reutilizar nombre tras eliminar |
| `idx_services_business_active`, `idx_resources_business_active` (parciales) | Listado de servicios/recursos activos (C1) |
| `idx_service_resources_resource` | Servicios por recurso |

## 5. Reglas garantizadas en la BD

| Regla | Mecanismo |
|---|---|
| Sin doble reserva del mismo recurso | EXCLUDE `ex_res_no_overlap` |
| Servicio y recurso del mismo negocio y vinculados | FK compuestas |
| Reserva futura, dentro del horario, sin bloqueo, con la duración del servicio, servicio/recurso activos | Trigger `trg_validate_reservation` (zona America/Bogota) |
| Reserva cancelada no se reactiva | Trigger `trg_reservation_status_guard` |
| No eliminar/desactivar servicio o recurso con reservas futuras confirmadas | Triggers `trg_block_deactivate_service/resource` |
| Eliminación lógica (historial intacto) | `deleted_at`; sin DELETE para `app_user` en tablas de negocio |
| Cancelación coherente (estado ⇔ fecha) | CHECK `chk_res_cancel` |
| `updated_at` automático | Trigger `fn_set_updated_at` |

Auditoría (sin cambio de esquema): nuevas acciones en `audit_logs.action`: `SERVICIO_CREADO`, `SERVICIO_ELIMINADO`, `RECURSO_CREADO`, `RECURSO_ELIMINADO`, `HORARIO_MODIFICADO`, `RESERVA_CREADA`, `RESERVA_CANCELADA`, `RESERVA_RECHAZADA`.
