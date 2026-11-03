# ADR-008 — Módulo de reportes como capa de solo lectura

* **Estado:** Propuesto. Implementado en el Sprint 3 (2026-11-02); pendiente de revisión del equipo.
* **Fecha:** 2026-11-02
* **Responsable:** Simon Betancur.
* **Decisión:** los reportes del Sprint 3 (HU-29 a HU-34) viven en un **módulo nuevo `report`, sin tablas propias**, que solo **lee** a través de interfaces de los demás módulos. Las sumas y los conteos los hace **la base de datos**; el módulo solo combina los resultados.

## 1. Contexto

El Sprint 3 pide seis consultas: ocupación por recurso (HU-29) y por negocio (HU-30), reservas por estado (HU-31), servicios más reservados (HU-32), valor de las reservas (HU-33) y un resumen de la plataforma para el administrador (HU-34). Todas cruzan datos de varios módulos: reservas (`reservation`), horarios y cierres (`resource`), nombres de servicios (`service`), pertenencia del negocio y negocios activos (`provider`) y la sesión (`identity`). El diseño del Sprint 1 ya preveía un componente `Report` encima de `Reservation`.

## 2. Alternativas

| Alternativa | Ventaja | Por qué no |
|---|---|---|
| Endpoints de reportes en cada módulo | Sin módulo nuevo | La ocupación necesita horario (`resource`) **y** reservas (`reservation`): habría que meterla en `reservation`, que se volvería un cajón de sastre |
| Leer las tablas directamente con SQL desde `report` | Una sola consulta por reporte | Rompe ADR-003/007: `report` dependería del esquema de otros módulos y un cambio de tabla lo rompería sin que el compilador avise |
| Vista materializada o tabla de estadísticas | Lecturas instantáneas | Exige refrescarla y sincronizarla; con el volumen del proyecto no hace falta (las consultas usan los índices `business_id, start_at`) |
| **Módulo `report` de solo lectura sobre interfaces (elegida)** | Respeta el grafo, cero tablas nuevas, cada módulo sigue dueño de sus datos | Una consulta agregada por cada dato que se combina |

## 3. Decisión

| # | Decisión |
|---|---|
| D1 | `report` es el módulo **más alto del grafo**: depende de `reservation`, `resource`, `service`, `provider` e `identity`; nadie depende de él. El verificador (`verificar_dependencias.py`) lo incluye y confirma que no hay ciclos. |
| D2 | `reservation` expone `BookingReportQuery`: conteos por estado y por origen de cancelación, sumas de `price_cop` por estado, conteos por servicio y **minutos ocupados por recurso y día** (SQL nativo agrupado: `group by resource_id, día de Bogotá`). Devuelve registros simples, nunca la entidad `Booking`. |
| D3 | `resource` expone `ResourceScheduleLookup.availableMinutesByDay`: minutos de atención efectivos (horario semanal **menos cierres** de HU-21) por recurso y día, calculados con **dos consultas** para todo el periodo. |
| D4 | **Ocupación** = minutos reservados (CONFIRMADA + COMPLETADA) ÷ minutos disponibles × 100, con un decimal; solo recursos **activos**; `null` si no hay minutos disponibles. Supuesto D1 del plan del Sprint 3, a confirmar con Calidad. |
| D5 | **Periodo** por el día de Bogotá del inicio de la reserva, `from`/`to` inclusivos, por defecto los últimos 30 días, máximo 366 (protege la base). |
| D6 | **Seguridad igual que el resto**: reportes de negocio solo para el PROVEEDOR dueño (`BusinessAccessService.requireOwner`, 403 si no); el de plataforma solo ADMINISTRADOR, que ya pasó por el MFA obligatorio (ADR-004). |
| D7 | El valor de las reservas usa el **precio guardado en cada reserva** (snapshot, ADR-006), no el precio actual del servicio: así un cambio de precio (HU-10) no altera lo ya reservado. Es una referencia; no hay pagos. |

## 4. Consecuencias

* **Positivas:** ninguna migración nueva para los reportes; los datos de cada módulo siguen encapsulados; los reportes reflejan siempre el estado real (no hay copia que sincronizar); el cálculo es probable con dobles (Mockito) y contra PostgreSQL real (`ReportIntegrationTest`, números exactos).
* **Negativas / límites:** el horario no tiene historial, así que la ocupación de un periodo pasado usa el horario **vigente**; un reporte de 366 días hace dos consultas de horario y una de reservas, más un recorrido en memoria de 366 × recursos (aceptable para el volumen académico; si creciera, la siguiente opción es una vista materializada). El resumen de plataforma cuenta los negocios con tres consultas sobre todos los proveedores.
* **Evolución:** si se separaran microservicios, `report` sería el candidato natural a servicio de lectura (CQRS) alimentado por eventos de `reservation`.
