# Plan de trabajo — Sprint 3 (Arquitectura de Software)

**Plataforma de Reservas de Servicios** · Redactado el **2026-11-02** · Fuente de las HU: `Sprint 3 HU para organizar.pdf` (Calidad/PO) y las HU que quedaron del Sprint 2 (`HUSprint2.md`).

> Documento vivo. El estado al día está en [`handoff-contexto.md`](../sprint-2/handoff-contexto.md) §9 (bitácora). Si algo de aquí contradice el código, gana el código.

## 1. Fechas

| Quién | Qué | Fechas |
|---|---|---|
| Calidad | Organiza las HU y define los casos de prueba | 2 → 6 nov |
| Arquisoft + BD | HU pendientes del Sprint 2 (antes del 7) | 2 → 6 nov |
| Arquisoft + BD | HU del Sprint 3 (29–34) | 7 → 16 nov |
| Calidad | Pruebas y automatización | 17 → 23 nov |
| Todos | Entrega final y sustentación del proyecto | después del 23 nov (fecha por confirmar) |

## 2. Alcance

### 2.1 HU pendientes del Sprint 2 (primero: HU-31 necesita HU-27 y HU-29/30 dependen de HU-21)

| HU | Qué | Módulo | Endpoint | Tabla |
|---|---|---|---|---|
| HU-27 | Completar reserva (proveedor) | `reservation` | `POST /api/v1/bookings/{id}/completion` | — |
| HU-07 | Editar información del negocio | `provider` | `PATCH /api/v1/businesses/{businessId}` | `businesses` + columnas (V13) |
| HU-10 | Editar servicio | `service` | `PATCH /api/v1/services/{serviceId}` | — |
| HU-11 | Desactivar servicio (con confirmación) | `service` + `reservation` | `POST /api/v1/services/{serviceId}/deactivation` | — |
| HU-12 | Reactivar servicio | `service` | `POST /api/v1/services/{serviceId}/reactivation` | — |
| HU-15 | Editar recurso | `resource` | `PATCH /api/v1/resources/{resourceId}` | — |
| HU-21 | Bloquear un horario puntual (cierres) | `resource` + `reservation` | `POST`/`GET /api/v1/resources/{id}/closures`, `DELETE …/closures/{closureId}` | `resource_closures` (V14) |

### 2.2 HU del Sprint 3 (reportes; módulo nuevo `report`, sin tablas propias)

| HU | Qué | Pts | Endpoint (`GET`) | Rol |
|---|---|---|---|---|
| HU-29 | Ocupación por recurso | 5 | `/api/v1/businesses/{id}/reports/resource-occupancy?from=&to=` | PROVEEDOR dueño |
| HU-30 | Ocupación del negocio por día o semana | 3 | `/api/v1/businesses/{id}/reports/occupancy?from=&to=&groupBy=DAY\|WEEK` | PROVEEDOR dueño |
| HU-31 | Reservas por estado | 3 | `/api/v1/businesses/{id}/reports/bookings-by-status?from=&to=` | PROVEEDOR dueño |
| HU-32 | Servicios más reservados | 3 | `/api/v1/businesses/{id}/reports/top-services?from=&to=&limit=` | PROVEEDOR dueño |
| HU-33 | Valor de las reservas | 5 | `/api/v1/businesses/{id}/reports/booking-value?from=&to=` | PROVEEDOR dueño |
| HU-34 | Resumen de la plataforma | 8 | `/api/v1/admin/reports/platform-summary?from=&to=` | ADMINISTRADOR (con MFA) |

**Reglas comunes de los reportes** (del PDF): el proveedor solo ve su negocio y el cliente no tiene acceso (403); sin sesión, 401; `from ≤ to` o 400; si no hay datos, el reporte sale vacío con `message`.

### 2.3 Entregables de Arquitectura (en paralelo al desarrollo)

| Entregable | Dónde |
|---|---|
| Informe final del proyecto (todo el semestre, compactado) | [`docs/entrega-final/informe-final-proyecto.md`](../entrega-final/informe-final-proyecto.md) |
| Guía para la sustentación (arquitectura, decisiones, código clave, preguntas difíciles) | [`docs/entrega-final/guia-sustentacion.md`](../entrega-final/guia-sustentacion.md) |
| Diagramas actualizados (componentes, paquetes/módulos, despliegue con Neon, modelo de datos) | `docs/arquitectura/diagramas/` |
| ADR-008 (módulo de reportes como capa de consulta) | `docs/arquitectura/adr/ADR-008-modulo-de-reportes.md` |
| Swagger, colección de Postman y referencia de la API con los endpoints nuevos | `docs/api/`, `docs/postman/` |

## 3. Decisiones de diseño (propuestas; las confirma Calidad/PO)

| # | Decisión | Por qué | Alternativa |
|---|---|---|---|
| D1 | **Ocupación = minutos reservados (CONFIRMADA + COMPLETADA) ÷ minutos disponibles** según el horario semanal **actual** de los recursos **activos**, menos sus cierres (HU-21), en el periodo | Es la definición del PDF; el horario no tiene historial, así que se usa el vigente; un recurso inactivo no se puede reservar y sumaría horas «libres» falsas | Guardar el historial de horarios (más tablas, sin pedido explícito); incluir recursos inactivos |
| D2 | Reportes en un **módulo nuevo `report`** que solo **lee por interfaces** de los demás módulos (sin tablas propias); las agregaciones (conteos, sumas) se hacen en la base | Mantiene el grafo sin ciclos (el módulo queda encima de `reservation`); la base agrega más rápido que la aplicación | Consultas sueltas en cada módulo (dispersa la lógica) o una vista materializada (innecesaria con este volumen) |
| D3 | Periodo por **día de Bogotá del inicio de la reserva**, inclusivo; por defecto los últimos 30 días; máximo 366 días | Igual que HU-24 (`from`/`to`); el tope protege la base | Exigir siempre las fechas |
| D4 | **HU-27:** solo el proveedor dueño; la reserva debe estar CONFIRMADA y **su hora de fin ya debe haber pasado** | AC: «la fecha y hora del servicio ya han transcurrido». No existe el estado «En proceso» | Permitir completar desde el inicio |
| D5 | La calificación o reseña (HU-27) **queda fuera**: no hay HU que la defina | Sin criterios no se puede probar | Diseñarla sin HU |
| D6 | **HU-07:** `PATCH` parcial: campo ausente o `null` = no cambia; cadena vacía en un dato opcional = se borra; nombre vacío → 400 | AC «edición de un solo dato» y «datos opcionales sin diligenciar» | `PUT` completo (obliga a reenviar todo) |
| D7 | **Tipos de negocio:** lista fija `SALUD`, `BELLEZA`, `BIENESTAR`, `DEPORTE`, `EDUCACION`, `CONSULTORIA`, `ENTRETENIMIENTO`, `OTRO` | El AC rechaza «Panadería» y «123»: tiene que ser una lista cerrada | Tabla de tipos administrable |
| D8 | **Teléfono del negocio:** 10 dígitos, celular (`3…`) o fijo nacional (`60…`) | AC rechaza `abcde12345`, `12345` y `123`; en Colombia todos los números tienen 10 dígitos | Solo celular |
| D9 | **HU-11:** mismo patrón que HU-16: sin `confirm` y con reservas futuras → `409 CONFIRMATION_REQUIRED`; con `confirm` cancela con origen `SERVICIO_NO_DISPONIBLE` en la misma transacción | Coherencia con lo ya aprobado en el Sprint 2 (ADR-006) | Otro contrato |
| D10 | **HU-21:** cierre de **día completo** (`date`) o de un **rango** (`start`/`end` con fecha y hora); la base impide cierres traslapados del mismo recurso con un `EXCLUDE` (como ADR-005) | El AC tiene los dos casos y ejemplos con fecha y hora | Un cierre por día sin rangos |
| D11 | Un cierre **se descuenta del horario efectivo** del recurso (`ResourceScheduleLookup`), así que la disponibilidad (HU-20) y la creación de reservas (HU-22) lo respetan sin cambiar su lógica; al reservar se vuelve a comprobar con el recurso bloqueado | Una sola fuente de verdad del horario; evita la carrera cierre-reserva | Un `BusyTimeSource` en `resource` (crearía un ciclo `resource → service`) |
| D12 | El «aviso al cliente» de HU-21/HU-11 = evento de auditoría (no hay correo) | Igual que HU-25/26 en el Sprint 2 | Integrar correo |
| D13 | **HU-34:** «negocios activos» = negocios cuyo proveedor tiene la cuenta habilitada | No hay estado propio del negocio | Agregar un estado al negocio |

## 4. Riesgos

| Riesgo | Mitigación |
|---|---|
| Render gratuito: 750 h/mes y arranque en frío | Base en Neon (no expira); calentar el servicio antes de las pruebas de Calidad (17-23 nov) |
| La definición de ocupación (D1) puede no ser la que espera Calidad | Se documenta como supuesto y se confirma en la semana del 2-6 nov |
| La propuesta de BD `docs/bd/propuestas/V13__…` choca con la numeración | La V13 la toma HU-07; la propuesta de BD pasa al siguiente número libre si se aprueba |
| Volumen: reportes sobre muchas reservas | Agregación en SQL con los índices existentes (`business_id, start_at`); tope de 366 días |

## 5. Azure DevOps

Una tarea por componente (política del 2026-10-07): ver [`azure-boards/README.md`](azure-boards/README.md). Las historias HU-29…34 las crea Calidad/PO en Azure; las tareas se suben con el lote `S3-L01`.
