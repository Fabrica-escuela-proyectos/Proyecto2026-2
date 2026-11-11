# -*- coding: utf-8 -*-
"""
Lote de Azure DevOps del Sprint 3 (proceso Agile: User Story / Task). Misma política que el Sprint 2
(docs/sprint-2/azure-boards/README.md): POCAS tareas, una por componente; bugs, pruebas y documentación suelta
no generan tarea.

    python docs/sprint-3/azure-boards/generar_sprint3.py

Genera lotes/azure-import_S3-L01_sprint-3.csv y lotes/asignaciones-S3-L01.md (responsables y cierre).

PARA NO MODIFICAR LO DE OTROS (lección del Sprint 2): una historia que ya existe solo puede llevar tareas si su
fila va en el CSV con su ID, y Azure aplica a esa fila todas las columnas del archivo. Por eso:
- el CSV trae SOLO las columnas que necesitan las tareas (nada de Estado, Descripción, Asignado, Etiquetas,
  Prioridad ni Puntos, que en la fila de una historia ajena quedarían vacías o con otro valor);
- la fila de una historia existente lleva su ID, su título EXACTO como está en Azure (TITULOS_AZURE: un título
  distinto la renombraría) y la iteración del Sprint 3 (en la que ya están);
- una historia conocida sin ID o sin título hace fallar el script en vez de crearla o adivinarlo;
- las tareas ya creadas a mano van en YA_CREADAS y no se vuelven a importar.
Azure crea las tareas importadas en New y sin responsable (no deja fijar Estado ni Assigned To al crear): se
asignan y cierran después con edición masiva siguiendo lotes/asignaciones-S3-L01.md.
Antes de pulsar "Save items", revisa en la vista previa que las filas de las historias no muestren otros cambios.
"""
import csv
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
ITERACION = r"Service Booking Plataform\Sprint 3"
COLUMNAS = ["ID", "Work Item Type", "Title 1", "Title 2", "Activity", "Original Estimate", "Remaining Work",
            "Completed Work", "Iteration Path"]
RESPONSABLES = {"A": "Simon Betancur", "B": "Juan Esteban González", "C": "Santiago Rendón"}

# Historias que ya existen en Azure: código -> ID (None = existe pero falta anotar su ID).
STORY_IDS = {"HU-07": 72, "HU-10": 75, "HU-11": 76, "HU-12": 77, "HU-15": 80, "HU-21": 86, "HU-27": 93,
             "HU-29": 141}
# HU del PDF "Sprint 3 HU para organizar" que NO existen en Azure (2026-11-04): las crea Calidad/PO, no este lote.
# Sus tareas se omiten hasta que existan; entonces anotar su ID aquí arriba y su título exacto en TITULOS_AZURE.
SIN_HISTORIA_EN_AZURE = {"HU-30", "HU-31", "HU-32", "HU-33", "HU-34"}
# Título EXACTO de cada historia existente en Azure (copiado de Azure, no del documento de HU).
TITULOS_AZURE = {
    72: "HU 07 - Editar información del negocio",
    75: "HU 10 - Editar servicio",
    76: "HU 11 - Desactivar servicio",
    77: "HU 12 - Reactivar servicio",
    80: "HU 15 - Editar recurso",
    86: "HU 21 - Bloquear un horario puntual",
    93: "HU 27 - Completar reserva",
    141: "HU-29 Consultar ocupación de los recursos.",
}
# Tareas ya creadas a mano en Azure (código -> ID): no se importan otra vez.
YA_CREADAS = {"HU29-API": 142}
TECH_IDS = {}  # "TECH-04": <ID> después de importar (es la única historia que crea el lote)

# Responsable (A/B/C) por pistas de la guía de sustentación §9, con horas equilibradas.
# (código de HU, tareas: (código, título, horas, responsable))
HISTORIAS = [
    ("HU-07", [("HU07-DATOS", "Perfil del negocio: columnas descripción, dirección, tipo y teléfono (migración V13)", 3, "A"),
               ("HU07-API", "Perfil del negocio: edición parcial (PATCH /api/v1/businesses/{id}) y datos visibles en el catálogo", 10, "A")]),
    ("HU-10", [("HU10-API", "Módulo de servicios: editar servicio (PATCH /api/v1/services/{id}) sin alterar el precio de las reservas", 8, "B")]),
    ("HU-11", [("HU11-API", "Ciclo de vida del servicio: desactivar con confirmación y cancelación de reservas futuras", 10, "B")]),
    ("HU-12", [("HU12-API", "Ciclo de vida del servicio: reactivar (idempotente, sin restaurar canceladas)", 4, "B")]),
    ("HU-15", [("HU15-API", "Módulo de recursos: editar nombre y tipo (PATCH /api/v1/resources/{id})", 6, "C")]),
    ("HU-21", [("HU21-DATOS", "Cierres puntuales: tabla resource_closures con EXCLUDE anti-traslape (migración V14)", 3, "C"),
               ("HU21-API", "Cierres puntuales: crear con confirmación, listar y eliminar; horario efectivo en disponibilidad y reservas", 14, "C")]),
    ("HU-27", [("HU27-API", "Módulo de reservas: completar reserva (POST /api/v1/bookings/{id}/completion)", 5, "B")]),
    ("HU-29", [("HU29-API", "Módulo de reportes: base del módulo (ADR-008) y ocupación por recurso", 12, "A")]),
    ("HU-30", [("HU30-API", "Módulo de reportes: ocupación del negocio por día o semana con mayor y menor demanda", 6, "C")]),
    ("HU-31", [("HU31-API", "Módulo de reportes: reservas por estado y cancelaciones por origen", 4, "B")]),
    ("HU-32", [("HU32-API", "Módulo de reportes: ranking de servicios más reservados", 4, "B")]),
    ("HU-33", [("HU33-API", "Módulo de reportes: valor de las reservas según el precio guardado", 4, "B")]),
    ("HU-34", [("HU34-API", "Módulo de reportes: resumen de la plataforma para el administrador (con MFA)", 6, "A")]),
]

# Historia técnica nueva (no existe en Azure): la crea el lote.
TECNICAS = [
    ("TECH-04", "TECH-04 · Entrega final de Arquitectura de Software", [
        ("ARQ-05", "Diagramas de la entrega final: componentes y paquetes generados del código; despliegue con Neon", 6, "C", "Design"),
        ("ARQ-06", "Informe final del proyecto y guía de sustentación (arquitectura, decisiones y código clave)", 8, "A", "Documentation")]),
]


def fila(**kw):
    r = {c: "" for c in COLUMNAS}
    r.update(kw)
    return r


def historia_existente(codigo):
    sid = STORY_IDS.get(codigo)
    if sid is None:
        sys.exit("La historia %s ya existe en Azure: anota su ID en STORY_IDS y su título exacto en TITULOS_AZURE." % codigo)
    titulo = TITULOS_AZURE.get(sid)
    if not titulo:
        sys.exit("Falta el título exacto de la historia %s (ID %d) en TITULOS_AZURE: sin él la importación la renombraría."
                 % (codigo, sid))
    return fila(**{"ID": sid, "Work Item Type": "User Story", "Title 1": titulo, "Iteration Path": ITERACION})


def tarea(codigo, titulo, horas, activity="Development"):
    # Hecha: estimación = completado y restante 0.
    return fila(**{"Work Item Type": "Task", "Title 2": "[%s] %s" % (codigo, titulo), "Activity": activity,
                   "Original Estimate": horas, "Remaining Work": 0, "Completed Work": horas, "Iteration Path": ITERACION})


def guia_asignaciones(por_persona):
    L = ["# Asignación y cierre de las tareas del lote S3-L01 (generado por `generar_sprint3.py`)", "",
         "Azure crea las tareas importadas en `New` y sin responsable. Después de pulsar *Save items*:", "",
         "1. *Boards → Queries → New query*, filtros: `Iteration Path` = `Service Booking Plataform\\Sprint 3`, "
         "`Work Item Type` = `Task`, `Title` *Contains* `[` → *Run query*.",
         "2. Por cada persona: seleccionar sus tareas (Ctrl+clic sobre el código entre corchetes) → clic derecho → "
         "*Edit* → campo `Assigned To` = la persona → *Save*.",
         "3. Seleccionar **todas** las tareas → clic derecho → *Edit* → `State` = `Closed` → *Save*.",
         "4. Ninguno de estos pasos toca las historias: el estado y el responsable de cada HU los decide quien la tiene.", "",
         "| Responsable | Tareas | Horas |", "|---|---|---|"]
    for o in ("A", "B", "C"):
        tareas = por_persona.get(o, [])
        L.append("| %s | %s | %d |" % (RESPONSABLES[o], ", ".join("`%s`" % c for c, _ in tareas), sum(h for _, h in tareas)))
    L += ["", "`HU29-API` es la tarea **142**, creada a mano en la historia 141: se edita a mano (título, responsable, "
          "esfuerzo y `Closed`) y no va en el CSV.", ""]
    return "\n".join(L)


def main():
    filas = []
    por_persona = {}
    for hu, tareas in HISTORIAS:
        for c, _, h, o in tareas:
            por_persona.setdefault(o, []).append((c, h))
        pendientes = [t for t in tareas if t[0] not in YA_CREADAS]
        if hu in SIN_HISTORIA_EN_AZURE and hu not in STORY_IDS:
            esperando = globals().setdefault("ESPERANDO", [])
            esperando += [t[0] for t in pendientes]
            continue
        if not pendientes:
            continue  # nada que importar: la fila de la historia ni siquiera se incluye
        filas.append(historia_existente(hu))
        filas += [tarea(c, t, h) for c, t, h, _ in pendientes]
    for codigo, titulo, tareas in TECNICAS:
        if codigo in TECH_IDS:
            filas.append(fila(**{"ID": TECH_IDS[codigo], "Work Item Type": "User Story", "Title 1": titulo,
                                 "Iteration Path": ITERACION}))
        else:
            filas.append(fila(**{"Work Item Type": "User Story", "Title 1": titulo, "Iteration Path": ITERACION}))
        for c, t, h, o, act in tareas:
            por_persona.setdefault(o, []).append((c, h))
            filas.append(tarea(c, t, h, act))

    os.makedirs(os.path.join(AQUI, "lotes"), exist_ok=True)
    destino = os.path.join(AQUI, "lotes", "azure-import_S3-L01_sprint-3.csv")
    with open(destino, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNAS)
        w.writeheader()
        w.writerows(filas)
    en_espera = set(globals().get("ESPERANDO") or [])
    por_persona = {o: [t for t in ts if t[0] not in en_espera] for o, ts in por_persona.items()}
    open(os.path.join(AQUI, "lotes", "asignaciones-S3-L01.md"), "w", encoding="utf-8").write(guia_asignaciones(por_persona))

    tareas_n = sum(1 for r in filas if r["Work Item Type"] == "Task")
    existentes = [r["ID"] for r in filas if r["Work Item Type"] == "User Story" and r["ID"]]
    nuevas = [r["Title 1"] for r in filas if r["Work Item Type"] == "User Story" and not r["ID"]]
    print("Lote S3-L01: %d tareas -> %s" % (tareas_n, os.path.relpath(destino, AQUI)))
    print("  historias existentes (solo se les cuelgan tareas): %s" % existentes)
    print("  historias que se CREAN: %s" % (nuevas or "ninguna"))
    print("  tareas omitidas por ya existir: %s" % (sorted(YA_CREADAS) or "ninguna"))
    print("  tareas en espera (su HU no existe en Azure): %s" % (globals().get("ESPERANDO") or "ninguna"))
    for o in ("A", "B", "C"):
        print("  %s: %d h" % (RESPONSABLES[o], sum(h for _, h in por_persona.get(o, []))))


if __name__ == "__main__":
    main()
