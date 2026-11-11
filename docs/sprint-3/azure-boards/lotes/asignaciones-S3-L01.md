# Asignación y cierre de las tareas del lote S3-L01 (generado por `generar_sprint3.py`)

Azure crea las tareas importadas en `New` y sin responsable. Después de pulsar *Save items*:

1. *Boards → Queries → New query*, filtros: `Iteration Path` = `Service Booking Plataform\Sprint 3`, `Work Item Type` = `Task`, `Title` *Contains* `[` → *Run query*.
2. Por cada persona: seleccionar sus tareas (Ctrl+clic sobre el código entre corchetes) → clic derecho → *Edit* → campo `Assigned To` = la persona → *Save*.
3. Seleccionar **todas** las tareas → clic derecho → *Edit* → `State` = `Closed` → *Save*.
4. Ninguno de estos pasos toca las historias: el estado y el responsable de cada HU los decide quien la tiene.

| Responsable | Tareas | Horas |
|---|---|---|
| Simon Betancur | `HU07-DATOS`, `HU07-API`, `HU29-API`, `ARQ-06` | 33 |
| Juan Esteban González | `HU10-API`, `HU11-API`, `HU12-API`, `HU27-API` | 27 |
| Santiago Rendón | `HU15-API`, `HU21-DATOS`, `HU21-API`, `ARQ-05` | 29 |

`HU29-API` es la tarea **142**, creada a mano en la historia 141: se edita a mano (título, responsable, esfuerzo y `Closed`) y no va en el CSV.
