# Conexiones — Diagrama de Paquetes

| Paquete dependiente | Etiqueta | Paquete del cual depende |
|---|---|---|
| report | datos de reservas | reservation |
| reservation | servicio | service |
| reservation | recurso | resource |
| service | pertenencia | provider |
| resource | pertenencia | provider |
| provider | identidad / autorizacion | identity |
| reservation | identidad / autorizacion | identity |
| service | autorizacion | identity |
| resource | autorizacion | identity |
| report | autorizacion | identity |
| identity | auditoria | audit |
| provider | auditoria | audit |
| reservation | auditoria | audit |
| service | auditoria | audit |
| resource | auditoria | audit |
