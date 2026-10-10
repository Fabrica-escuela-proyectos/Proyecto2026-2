# Documentación BD — Sprint 2
Responsable: ANDRAUS LOPEZ JUAN SEBASTIAN

## Entregables
| Día plan | Entregable | Archivo |
|---|---|---|
| 1–3 | MER, entidades, relaciones, índices | `modelo_sprint2.md` |
| 2–3 | Modelo físico + tablas nuevas + scripts | `script_sprint2.sql` (incremental) · `script_completo_s1_s2.sql` (BD nueva) |
| 4–5 | Consultas de disponibilidad, reservas y cancelaciones | `consultas_sprint2.sql` (C1–C8) |
| 6 | Seguridad BD (RLS, privilegios) | `seguridad_bd_sprint2.sql` |
| 7 | Pruebas BD y volumen | `pruebas_bd_sprint2.sql` · `pruebas_volumen_sprint2.sql` |

## Cómo ejecutar
- **BD con Sprint 1 ya aplicado (tu caso en Supabase):** SQL Editor → `script_sprint2.sql` → `seguridad_bd_sprint2.sql`. Son re-ejecutables.
- **BD nueva:** solo `script_completo_s1_s2.sql`. No mezclar con `script_inicial.sql` ni con `script_sprint2.sql` (duplicaría objetos).
- Después, cambiar la contraseña de `app_user` (`CHANGE_ME` es solo de ejemplo).
- Las pruebas usan transacción con `ROLLBACK`; ejecutar solo en BD de pruebas.

## Resultados de pruebas (PostgreSQL 16 local; no ejecutadas aún en Supabase)
**Integridad: 15/15 casos OK** — reserva válida, doble reserva, fuera de horario, pasado, duración incorrecta, recurso no asociado, horarios traslapados, horario inválido, eliminar con reservas futuras (servicio y recurso), cancelación incoherente, estado inválido, bloqueo, re-reserva tras cancelar, reactivar cancelada.

**Volumen: 200.000 reservas, 500 recursos, 20.000 clientes** (carga ≈ 8 s)

| Consulta | Tiempo | Plan |
|---|---|---|
| C2 disponibilidad | ≈ 0,3 ms | índice GiST de reservas y horarios |
| C4 reservas del cliente | ≈ 0,3 ms | `idx_reservations_client` |
| C5 reservas del negocio (mes) | ≈ 0,7 ms | `idx_reservations_service` |
| C7 tasa de cancelación mensual | ≈ 100–130 ms | Seq Scan paralelo (reporte agregado sobre ~17 % de la tabla) |
| Doble reserva con 200k filas | rechazada | `ex_res_no_overlap` |

C7 es un reporte, no una ruta crítica; no se agrega índice. Reevaluar si el volumen crece por orden de magnitud (índice por `starts_at` o particionado por mes).

## Trazabilidad HU → BD
La planificación solo agrupa las HU por día; este mapeo debe validarse contra el texto de cada HU.

| HU | Tema | Tablas | Consultas |
|---|---|---|---|
| HU-07 a HU-15 | Servicios y recursos | services, resources, service_resources | C1, C8 |
| HU-16 a HU-21 | Horarios y disponibilidad | resource_schedules, resource_blocks | C2 |
| HU-22 a HU-24 | Reservas | reservations | C3, C4, C5 |
| HU-25 a HU-28 | Cancelación, eliminación, MFA | reservations, services/resources (`deleted_at`), sessions (`mfa_verified_at`) | C6, C7, C8 |

## Supuestos y pendientes a validar con Arquitectura
- Zona horaria de negocio fija: `America/Bogota` (en `fn_validate_reservation` y en C2).
- Duración de la reserva = duración del servicio; `ends_at` lo calcula el backend.
- Sin política de anticipación mínima para cancelar ni capacidad por recurso (no están en la información disponible). Si las HU las exigen, se agregan como CHECK/trigger y columna `capacity`.
- Errores de negocio salen como excepción con código en el mensaje (`RESERVA_FUERA_DE_HORARIO`, `RECURSO_BLOQUEADO`, …); el backend debe mapearlos a 400/409 según `errores-api-sprint-1.md`.
- Verificar en Supabase: extensión `btree_gist` y tipo `timerange` (el script los crea; si el editor reporta permisos, activar `btree_gist` desde Database → Extensions).
