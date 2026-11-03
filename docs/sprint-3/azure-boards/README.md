# Azure DevOps — Sprint 3

Misma política que el Sprint 2 ([`../../sprint-2/azure-boards/README.md`](../../sprint-2/azure-boards/README.md)): **una tarea por componente**; bugs, pruebas y documentación suelta no generan tarea.

1. Si Calidad/PO ya crearon las historias HU-29…34 en Azure, anotar sus IDs en `STORY_IDS` de [`generar_sprint3.py`](generar_sprint3.py) (si no, el CSV las crea).
2. `python docs/sprint-3/azure-boards/generar_sprint3.py` → `lotes/azure-import_S3-L01_sprint-3.csv` (17 tareas).
3. Verificar que exista la iteración `Service Booking Plataform\Sprint 3` y subir el CSV en *Boards → Work items → Import work items*.
4. Después de importar: anotar el ID de `TECH-04` en `TECH_IDS` y no volver a importar este lote (crearía duplicados).

Estado del trabajo al 2026-11-02: todas las tareas de HU (HU07…HU34) tienen el código y las pruebas hechos **sin commitear**; pasan a *Closed* cuando se fusione el PR revisado. `ARQ-05` y `ARQ-06` quedan *Active* hasta la entrega.
