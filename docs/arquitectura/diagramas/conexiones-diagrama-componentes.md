# Conexiones — Diagrama de componentes (generada del código)

Respaldo del diagrama [`diagrama-componentes.svg`](diagrama-componentes.svg). La regenera `python docs/arquitectura/diagramas/generar_diagrama_componentes.py`; no editar a mano.

| Interfaz | La provee | La requieren |
|---|---|---|
| `BookingReportQuery` | reservation | report |
| `BusyTimeSource` | reservation | service |
| `ServiceLifecycleService` | service | reservation |
| `ServiceLookupService` | service | report, reservation |
| `ResourceClosureService` | resource | reservation |
| `ResourceLifecycleService` | resource | reservation |
| `ResourceLookupService` | resource | report, reservation, service |
| `ResourceScheduleLookup` | resource | report, reservation, service |
| `BusinessAccessService` | provider | report, reservation, service, resource |
| `BusinessDirectoryService` | provider | report, reservation, service |
| `BusinessSettingsService` | provider | reservation, service |
| `AccountStatusService` | identity | provider |
| `IdentityService` | identity | report, reservation, service, resource, provider |
| `UserDeletionRequested` | identity | reservation |
| `UserDirectoryService` | identity | reservation |
| `UserProvisioningService` | identity | provider |
| `AuditService` | audit | reservation, service, resource, provider, identity |
