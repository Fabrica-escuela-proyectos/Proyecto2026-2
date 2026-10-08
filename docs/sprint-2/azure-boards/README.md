# Azure Boards — tareas del Sprint 2 (por componente)

Tareas listas para subir al Azure DevOps del proyecto (proceso **Agile**: `User Story` / `Task`, como en el export `HUSprint2.md`). Todo se genera desde una sola fuente de datos para que Azure, la trazabilidad y los documentos no se desincronicen.

## Política (decidida el 2026-10-07)

> **Azure lleva pocas tareas y de nivel arquitectónico: una por cada componente o parte desarrollada de la aplicación.**

| ¿Qué es el trabajo? | ¿Lleva tarea en Azure? |
|---|---|
| Un componente o parte nueva de la aplicación (módulo, capa de datos, API de una HU, pipeline, Swagger, MFA…) | **Sí**, una tarea general |
| Corrección o bug (issues de Calidad `#8`…), refactor, ajuste | **No** → va en el PR con `Fixes #n` |
| Pruebas, documentación suelta, ADR menores, estudio, ensayos, higiene (rotar claves…) | **No** → forman parte de la Definición de hecho de la tarea o del checklist interno |
| Ya lo cubre una tarea existente (`HU09-API`, `SEG-01`…) | **No se agrega nada**: se mueve esa tarea a `Active` / `Closed` |

Resultado actual: **28 tareas / 348 h** en un solo lote (`L01`), frente a las 173 tareas de la primera versión (descartada; nunca se subió).

| Archivo | Para qué sirve | ¿Se edita a mano? |
|---|---|---|
| `backlog_data.py` | **Fuente única**: HU del sprint, componentes, tareas técnicas, checklist interno, responsables | **Sí** (solo este) |
| `generar.py` | Valida los datos y genera todo lo demás (`python docs/sprint-2/azure-boards/generar.py`) | No |
| `lotes/azure-import_Lxx_*.csv` | CSV importables a Azure, uno por lote | No (se regeneran) |
| `tareas-sprint-2.md` | Vista legible: resumen, lotes, horas por responsable, tareas por historia y el checklist interno | No (generado) |
| `trazabilidad-sprint-2.md` | Matriz HU → regla → API → módulo → tabla → casos de prueba | No (generado) |

## 1. Qué contiene el lote L01

- **Por cada HU del sprint** (las que ya existen en Azure, IDs 72–93): una tarea **`HUnn-DATOS`** (modelo de datos + migración Flyway) si la HU crea tablas, y una tarea **`HUnn-API`** (reglas de negocio + endpoints, con autorización, auditoría y pruebas dentro de la tarea). Las HU sin tablas nuevas llevan solo `HUnn-API`. → 20 tareas.
- **Tres historias técnicas nuevas** con 8 tareas en total:
  - `TECH-01` Seguridad de acceso → `SEG-01` MFA obligatorio para administradores · `SEG-02` control de intentos reutilizable.
  - `TECH-02` Plataforma → `PLT-01` pipeline CI/CD y entorno de pruebas · `PLT-02` análisis de calidad (SonarCloud).
  - `TECH-03` Arquitectura, API y seguridad → `ARQ-01` Swagger · `ARQ-02` modelo de datos y migraciones con BD · `ARQ-03` diagramas y ADRs · `ARQ-04` revisión OWASP Top 10.
- El detalle fino (`MFA-02`, `SP1-01`, `SEC-01`, `BUG-8`…) sigue existiendo como **checklist interno** (`PENDIENTES`): sirve para estimar capacidad y se cita en los documentos, pero **no genera filas en los CSV**. Cada tarea técnica lista en su descripción los ítems que agrupa y suma sus horas.

## 2. Importar un lote a Azure (portal web)

1. Azure DevOps → tu proyecto → **Boards → Queries → Import Work Items** (o, en la vista de *Work items*, el menú `⋯ → Import Work Items`).
2. Elegir el CSV (`lotes/azure-import_L01_tareas-por-componente.csv`) → **Import**.
3. Revisar la vista previa: las filas con error salen en rojo. → **Save items**.
4. Marcar el lote como subido en `ESTADO_LOTES` de `backlog_data.py`. Un lote ya subido **no se vuelve a importar** (duplicaría).

> Si en algún momento se importó alguna de las CSV de la primera versión (L01–L07, 173 tareas), hay que borrar esos elementos antes: filtrar por la etiqueta `sprint2` en *Boards → Queries* y eliminarlos. Los de la versión actual también usan esa etiqueta, así que hazlo **antes** de importar la nueva.

**Cómo se arma la jerarquía:** el CSV usa las columnas `Title 1` (padre) y `Title 2` (tareas hijas). Los padres que **ya existen** en Azure (las HU) llevan su `ID` y solo repiten el título para anclar a sus hijas; las historias técnicas se crean nuevas con estado `New`.

**Si el importador no cuelga las tareas de la historia existente** (depende de la versión del portal): importar igual y, en *Boards → Backlogs*, usar el panel **Mapping** para arrastrar las tareas a su historia; o importar con la extensión de Excel de Azure DevOps. Si no quieres tocar las HU existentes, borra de la CSV las filas con `ID` y arrastra las tareas a mano.

## 3. Después de importar

- Poner **Assigned To** (la CSV lo deja vacío porque Azure exige el nombre exacto "NOMBRE <correo>"; rellenar `EQUIPO` en `backlog_data.py` lo evita en los lotes siguientes) y el **Iteration Path** "Sprint 2" (o rellenar `ITERATION_PATH`).
- Vincular las historias técnicas nuevas (`TECH-01`…) a su Feature/Épica. Épicas del sprint: 1 Proveedores y servicios · 2 Recursos · 3 Reservas.
- **Anotar los IDs que Azure asignó** a las historias técnicas en `TECH_IDS` (p. ej. `{"TECH-01": 145}`): los lotes posteriores cuelgan de ese ID en vez de crear otra historia.
- Estados de tarea: `New → Active → Closed`. Cada tarea lleva responsable y estimación (Lineamientos §3.6).

## 4. Qué hace Claude (u otra persona) en cada cambio

1. **Clasificar** el trabajo con la tabla de la política. Lo habitual es que *no* haya tarea nueva: se avisa qué tareas existentes pasan a `Active`/`Closed` (queda anotado en el handoff, §9).
2. **Solo si es un componente nuevo** que no cubre ninguna tarea: agregar una llamada en `backlog_data.py`, sección CAMBIOS:

   ```python
   # Ejemplo ilustrativo (NO es trabajo planificado): un componente que ninguna tarea actual cubre.
   chg("CHG-001", "Módulo de reportes de ocupación: consulta agregada por negocio (API REST)",
       "Development", 8, padre="HU-24", lote="L02")      # padre: una HU o "TECH-01".."TECH-03"
   ```
   - `codigo` único y estable (`CHG-nnn`); se usa en el commit/PR: `[CHG-001] …`.
   - `lote` **nuevo** por cada tanda (`L02`, `L03`, …); nunca reutilizar uno ya subido (el generador lo impide).
   - `estado="Closed"` si ya está hecho (las horas son el trabajo completado); `"New"` si está por hacer.
3. Correr `python docs/sprint-2/azure-boards/generar.py` e importar **solo** el CSV del lote nuevo; marcarlo como subido en `ESTADO_LOTES`.
4. Si el cambio corrige un issue de Calidad: `Fixes #n` en el PR (sin tarea de Azure).

## 5. Convenciones

- **Códigos:** `HUnn-DATOS` / `HUnn-API` (tareas de HU), `SEG-nn` · `PLT-nn` · `ARQ-nn` (tareas técnicas), `CHG-nnn` (componentes nuevos). Checklist interno: `MFA-nn`, `SP1-nn`, `SEC-nn`, `CI-nn`, `API-nn`, `OWASP-nn`, `DOC-nn`, `BD-nn`, `EST-xx`, `BUG-n` (n = n.º del issue de GitHub). El código va en el título (`[HU09-API] …`) y en `Tags`.
- **Prioridad:** 1 = Tier 1 / bloqueante, 2 = Tier 2.
- **Horas:** esfuerzo nominal sin asistente de IA; incluye pruebas y revisión (Definición de hecho §9.1 de los Lineamientos); sirven para detectar capacidad vs demanda (ver el plan, §2).
- **Actividad (Activity):** `Development`, `Design`, `Deployment`.
- Los **`CP-*`** de los casos de prueba son provisionales; Calidad define los definitivos. Los bugs los reporta y cierra Calidad en GitHub Issues.
