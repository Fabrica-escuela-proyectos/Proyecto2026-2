# Conexiones — Diagrama de Componentes

| Requiere | Interfaz | La provee |
|---|---|---|
| Report | IAuthorizationService | Authorization |
| Report | IAuditService | Audit |
| Report | IReservationQueryService | Reservation |
| Reservation | IIdentityService | Identity & Access |
| Reservation | IAuthorizationService | Authorization |
| Reservation | IAuditService | Audit |
| Reservation | IServiceCatalogService | Service |
| Reservation | IResourceAvailabilityService | Resource |
| Service | IProviderDirectoryService | Provider |
| Service | IAuthorizationService | Authorization |
| Service | IAuditService | Audit |
| Resource | IProviderDirectoryService | Provider |
| Resource | IAuthorizationService | Authorization |
| Resource | IAuditService | Audit |
| Provider | IIdentityService | Identity & Access |
| Provider | IAuthorizationService | Authorization |
| Provider | IAuditService | Audit |
| Authorization | IIdentityService | Identity & Access |
| Authorization | IAuditService | Audit |
| Identity & Access | IAuditService | Audit |
