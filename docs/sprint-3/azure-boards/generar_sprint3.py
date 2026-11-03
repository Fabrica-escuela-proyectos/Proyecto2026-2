# -*- coding: utf-8 -*-
"""
Lote de Azure DevOps del Sprint 3 (proceso Agile: User Story / Task). Misma política que el Sprint 2
(docs/sprint-2/azure-boards/README.md): POCAS tareas, una por componente; bugs, pruebas y documentación suelta
no generan tarea.

    python docs/sprint-3/azure-boards/generar_sprint3.py

Genera lotes/azure-import_S3-L01_sprint-3.csv. Antes de importar:
- Las historias HU-29..34 las puede haber creado ya Calidad/PO en Azure: si es así, anota su ID en STORY_IDS y
  regenera (así el CSV cuelga las tareas de la historia existente en vez de crear otra).
- Verifica en Azure que exista la iteración ITERACION.
"""
import csv
import html
import os

AQUI = os.path.dirname(os.path.abspath(__file__))
ITERACION = r"Service Booking Plataform\Sprint 3"
COLUMNAS = ["ID", "Work Item Type", "Title 1", "Title 2", "Description", "Assigned To", "State", "Tags", "Activity",
            "Original Estimate", "Remaining Work", "Completed Work", "Priority", "Story Points", "Iteration Path"]

# Historias que ya existen en Azure (las del Sprint 2 que quedaron pendientes). Agregar HU-29..34 si ya existen.
STORY_IDS = {"HU-07": 72, "HU-10": 75, "HU-11": 76, "HU-12": 77, "HU-15": 80, "HU-21": 86, "HU-27": 93}
TECH_IDS = {}  # "TECH-04": <ID> después de importar

HISTORIAS = [
    ("HU-07", "HU 07 - Editar información del negocio", None, [
        ("HU07-DATOS", "Perfil del negocio: columnas descripción, dirección, tipo y teléfono (migración V13)", 3,
         "ALTER TABLE businesses con CHECK de tipo y teléfono. docs/api/endpoints-sprint-3.md §1."),
        ("HU07-API", "Perfil del negocio: edición parcial (PATCH /api/v1/businesses/{id}) y datos visibles en el catálogo", 10,
         "Solo el proveedor dueño; auditoría EDICION_NEGOCIO. Supuestos D6-D8 del plan del Sprint 3.")]),
    ("HU-10", "HU 10 - Editar servicio", None, [
        ("HU10-API", "Módulo de servicios: editar servicio (PATCH /api/v1/services/{id}) sin alterar el precio de las reservas", 8,
         "Bloqueo de fila del servicio; las reservas conservan su snapshot de precio (ADR-006).")]),
    ("HU-11", "HU 11 - Desactivar servicio", None, [
        ("HU11-API", "Ciclo de vida del servicio: desactivar con confirmación y cancelación de reservas futuras", 10,
         "Orquestado en reservation (patrón de HU-16); 409 CONFIRMATION_REQUIRED; bloqueo compartido en la reserva (ADR-005 §6).")]),
    ("HU-12", "HU 12 - Reactivar servicio", None, [
        ("HU12-API", "Ciclo de vida del servicio: reactivar (idempotente, sin restaurar canceladas)", 4, "")]),
    ("HU-15", "HU 15 - Editar recurso", None, [
        ("HU15-API", "Módulo de recursos: editar nombre y tipo (PATCH /api/v1/resources/{id})", 6, "")]),
    ("HU-21", "HU 21 - Bloquear un horario puntual", None, [
        ("HU21-DATOS", "Cierres puntuales: tabla resource_closures con EXCLUDE anti-traslape (migración V14)", 3, ""),
        ("HU21-API", "Cierres puntuales: crear con confirmación, listar y eliminar; horario efectivo en disponibilidad y reservas", 14,
         "Orquestado en reservation; ResourceScheduleLookup.effectiveWindowsOn; revalidación con el recurso bloqueado.")]),
    ("HU-27", "HU 27 - Completar reserva", None, [
        ("HU27-API", "Módulo de reservas: completar reserva (POST /api/v1/bookings/{id}/completion)", 5,
         "Solo tras la hora de fin (supuesto D4); sin calificación (D5).")]),
    ("HU-29", "HU 29 - Consultar ocupación de un recurso", 5, [
        ("HU29-API", "Módulo de reportes: base del módulo (ADR-008) y ocupación por recurso", 12,
         "Módulo report de solo lectura; BookingReportQuery y availableMinutesByDay. Definición de ocupación: supuesto D1.")]),
    ("HU-30", "HU 30 - Consultar ocupación del negocio", 3, [
        ("HU30-API", "Módulo de reportes: ocupación del negocio por día o semana con mayor y menor demanda", 6, "")]),
    ("HU-31", "HU 31 - Consultar reservas por estado", 3, [
        ("HU31-API", "Módulo de reportes: reservas por estado y cancelaciones por origen", 4, "")]),
    ("HU-32", "HU 32 - Consultar servicios más reservados", 3, [
        ("HU32-API", "Módulo de reportes: ranking de servicios más reservados", 4, "")]),
    ("HU-33", "HU 33 - Consultar el valor de las reservas", 5, [
        ("HU33-API", "Módulo de reportes: valor de las reservas según el precio guardado", 4, "")]),
    ("HU-34", "HU 34 - Consultar el resumen de la plataforma", 8, [
        ("HU34-API", "Módulo de reportes: resumen de la plataforma para el administrador (con MFA)", 6, "")]),
]

TECNICAS = [
    ("TECH-04", "TECH-04 · Entrega final de Arquitectura de Software", 5,
     "Diagramas actualizados y generados del código, ADR-008, informe final del proyecto y guía de sustentación.", [
         ("ARQ-05", "Diagramas de la entrega final: componentes y paquetes generados del código; despliegue con Neon", "Design", 6),
         ("ARQ-06", "Informe final del proyecto y guía de sustentación (arquitectura, decisiones y código clave)", "Documentation", 8)]),
]


def fila(**kw):
    r = {c: "" for c in COLUMNAS}
    r.update(kw)
    return r


def h(texto):
    return html.escape(texto).replace("\n", "<br>")


def main():
    filas = []
    for hu, titulo, puntos, tareas in HISTORIAS:
        if hu in STORY_IDS:
            # Pendiente del Sprint 2: se mueve a la iteración del Sprint 3.
            filas.append(fila(**{"ID": STORY_IDS[hu], "Work Item Type": "User Story", "Title 1": titulo, "State": "New",
                                 "Iteration Path": ITERACION}))
        else:
            filas.append(fila(**{"Work Item Type": "User Story", "Title 1": titulo, "State": "New",
                                 "Story Points": puntos or "", "Iteration Path": ITERACION,
                                 "Description": h("Sprint 3. Criterios: Sprint 3 HU para organizar.pdf y docs/api/endpoints-sprint-3.md.")}))
        for codigo, t, horas, desc in tareas:
            filas.append(fila(**{"Work Item Type": "Task", "Title 2": "[%s] %s" % (codigo, t), "State": "New",
                                 "Description": h(desc + ("\n" if desc else "") + "Incluye pruebas unitarias y de integración (Definición de hecho)."),
                                 "Activity": "Development", "Original Estimate": horas, "Remaining Work": horas,
                                 "Priority": 1, "Iteration Path": ITERACION}))
    for codigo, titulo, puntos, desc, tareas in TECNICAS:
        if codigo in TECH_IDS:
            filas.append(fila(**{"ID": TECH_IDS[codigo], "Work Item Type": "User Story", "Title 1": titulo, "State": "New"}))
        else:
            filas.append(fila(**{"Work Item Type": "User Story", "Title 1": titulo, "State": "New", "Story Points": puntos,
                                 "Description": h(desc), "Iteration Path": ITERACION}))
        for c, t, act, horas in tareas:
            filas.append(fila(**{"Work Item Type": "Task", "Title 2": "[%s] %s" % (c, t), "State": "New", "Activity": act,
                                 "Original Estimate": horas, "Remaining Work": horas, "Priority": 1, "Iteration Path": ITERACION}))

    os.makedirs(os.path.join(AQUI, "lotes"), exist_ok=True)
    destino = os.path.join(AQUI, "lotes", "azure-import_S3-L01_sprint-3.csv")
    with open(destino, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNAS)
        w.writeheader()
        w.writerows(filas)
    tareas = sum(1 for r in filas if r["Work Item Type"] == "Task")
    horas = sum(int(r["Original Estimate"]) for r in filas if r["Work Item Type"] == "Task")
    nuevas = sum(1 for r in filas if r["Work Item Type"] == "User Story" and not r["ID"])
    print("Lote S3-L01: %d tareas (%d h), %d historias nuevas -> %s" % (tareas, horas, nuevas, os.path.relpath(destino, AQUI)))


if __name__ == "__main__":
    main()
