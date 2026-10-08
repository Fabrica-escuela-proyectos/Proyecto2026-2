# -*- coding: utf-8 -*-
"""
Genera, a partir de backlog_data.py:
  - lotes/azure-import_<lote>_<nombre>.csv  (importables en Azure Boards > Importar elementos de trabajo)
  - tareas-sprint-2.md                      (vista legible; NO editar a mano)
  - trazabilidad-sprint-2.md                (matriz HU -> regla -> API -> módulo -> tabla -> prueba)

Uso:   python docs/sprint-2/azure-boards/generar.py        (solo biblioteca estándar de Python 3)

Política: a Azure solo suben tareas por componente (ver backlog_data.py). El checklist fino
(PENDIENTES) se publica en tareas-sprint-2.md como referencia interna y NO genera filas en los CSV.
"""
import csv
import html
import os
import sys
from collections import OrderedDict, defaultdict

import backlog_data as d

AQUI = os.path.dirname(os.path.abspath(__file__))
SALIDA_LOTES = os.path.join(AQUI, "lotes")

COLUMNAS = ["ID", "Work Item Type", "Title 1", "Title 2", "Description", "Assigned To", "State",
            "Tags", "Activity", "Original Estimate", "Remaining Work", "Completed Work", "Priority",
            "Story Points", "Iteration Path", "Area Path"]

LOTE_BASE = "L01"


def h(texto):
    """Texto plano -> HTML mínimo para los campos ricos de Azure."""
    if texto is None:
        return ""
    return html.escape(str(texto)).replace("\n", "<br>")


def asignado(owner):
    return d.EQUIPO.get(owner, "")


def estado_lote(lote):
    return d.ESTADO_LOTES.get(lote, "pendiente")


def nombre_lote(lote):
    return d.NOMBRE_LOTES.get(lote, "cambios")


def fila(**kw):
    r = {c: "" for c in COLUMNAS}
    r.update(kw)
    return r


class Item:
    """Una User Story (padre) con sus tareas hijas, perteneciente a un lote."""
    def __init__(self, lote, titulo, existing_id=None, desc="", tags="", puntos="", prioridad="", meta=None):
        self.lote, self.titulo, self.existing_id = lote, titulo, existing_id
        self.desc, self.tags, self.puntos, self.prioridad = desc, tags, puntos, prioridad
        self.meta = meta or {}
        self.tareas = []  # dicts

    def add(self, codigo, titulo, activity, horas, owner, prio, desc="", estado="New", tags="", cubre=None):
        self.tareas.append(dict(codigo=codigo, titulo=titulo, activity=activity, horas=horas, owner=owner,
                                prio=prio, desc=desc, estado=estado, tags=tags, cubre=cubre or []))


def pendientes_por_codigo():
    return {p[0]: p for p in d.PENDIENTES}


def construir():
    items = []
    pend = pendientes_por_codigo()

    # --- Tareas de las HU del sprint (padres = historias que ya existen en Azure) ---
    for hu in d.HUS:
        pid = hu["hu"]
        comp, resumen, tabla, una_sola = d.COMPONENTES[pid]
        n = pid.replace("-", "")
        horas = d.HORAS_POR_PTS[hu["pts"]]
        prio = 1 if hu["tier"] == 1 else 2
        tags = f"sprint2;{pid};tier{hu['tier']}"
        it = Item(LOTE_BASE, d.STORY_TITLES[pid], existing_id=d.STORY_IDS[pid],
                  meta=dict(hu=pid, pts=hu["pts"], tier=hu["tier"], owner=hu["owner"], deps=hu["deps"]))

        endpoints = "\n".join(f"- {e}" for e in hu["endpoints"])
        desc_api = ("Endpoints (propuesta; validar en el Día 1):\n" + endpoints
                    + "\nReglas de negocio y casos de prueba: docs/sprint-2/azure-boards/trazabilidad-sprint-2.md."
                    + "\nIncluye autorización por rol/pertenencia, auditoría y pruebas unitarias y de integración (Definición de hecho).")
        titulo_api = f"{comp}: {resumen} (reglas de negocio y API REST)"

        if una_sola:
            if hu["tablas"].startswith("("):
                desc_api += f"\nDatos: {hu['tablas']}"
            else:
                desc_api += f"\nIncluye su migración Flyway: {hu['tablas']}"
            it.add(f"{n}-API", titulo_api, "Development", sum(horas), hu["owner"], prio, desc=desc_api, tags=tags)
        else:
            it.add(f"{n}-DATOS", f"{comp}: modelo de datos y migración Flyway (tabla {tabla})", "Development",
                   horas[0], hu["owner"], prio,
                   desc=f"Tabla(s): {hu['tablas']}\nConvención: UUID, TIMESTAMPTZ y ON DELETE explícito "
                        f"(docs/conciliacion-modelo-bd-sprint-1.md). Incluye las pruebas del repositorio.",
                   tags=tags)
            it.add(f"{n}-API", titulo_api, "Development", sum(horas[1:]), hu["owner"], prio, desc=desc_api, tags=tags)
        items.append(it)

    # --- Historias técnicas y sus tareas por componente ---
    por_story = defaultdict(list)
    for t in d.TAREAS_TECNICAS:
        por_story[t["story"]].append(t)
    for sid, meta in d.TECH_STORIES.items():
        it = Item(meta["lote"], meta["titulo"], desc=meta["desc"], puntos=meta["puntos"],
                  tags=f"sprint2;tecnica;{sid}", prioridad=1, meta=dict(tech=sid))
        for t in por_story[sid]:
            horas = sum(pend[c][5] for c in t["cubre"])
            incluye = "\n".join(f"- {c}: {pend[c][3]}" for c in t["cubre"])
            it.add(t["codigo"], t["titulo"], t["activity"], horas, t["owner"], t["prio"],
                   desc=f"{t['desc']}\nIncluye:\n{incluye}", tags=f"sprint2;{sid}", cubre=t["cubre"])
        items.append(it)

    # --- Componentes nuevos registrados con chg(): se agrupan por (lote, padre) ---
    grupos = OrderedDict()
    for c in d.CAMBIOS:
        grupos.setdefault((c["lote"], c["padre"]), []).append(c)
    for (lote, padre), cambios in grupos.items():
        if padre in d.STORY_IDS:
            it = Item(lote, d.STORY_TITLES[padre], existing_id=d.STORY_IDS[padre], meta=dict(cambio=padre))
        elif padre in d.TECH_IDS:
            it = Item(lote, d.TECH_STORIES[padre]["titulo"], existing_id=d.TECH_IDS[padre], meta=dict(cambio=padre))
        else:
            meta = d.TECH_STORIES[padre]
            print(f"AVISO: {padre} se crearía de nuevo en {lote}; anota su ID de Azure en TECH_IDS para evitar duplicados.")
            it = Item(lote, meta["titulo"], desc=meta["desc"], puntos=meta["puntos"],
                      tags=f"sprint2;tecnica;{padre}", prioridad=2, meta=dict(tech=padre))
        for c in cambios:
            it.add(c["codigo"], c["titulo"], c["activity"], c["horas"], c["owner"], 3, desc=c["desc"],
                   estado=c["estado"], tags=f"sprint2;cambio;{c['codigo']}")
        items.append(it)
    return items


def validar(items):
    codigos = set()
    for it in items:
        for t in it.tareas:
            if t["codigo"] in codigos:
                sys.exit(f"Código de tarea de Azure duplicado: {t['codigo']}")
            codigos.add(t["codigo"])
            if t["owner"] not in d.EQUIPO:
                sys.exit(f"Responsable inválido en {t['codigo']}: {t['owner']}")

    pend = {}
    for p in d.PENDIENTES:
        if p[0] in pend:
            sys.exit(f"Pendiente interno duplicado: {p[0]}")
        pend[p[0]] = p
        if p[6] not in d.EQUIPO:
            sys.exit(f"Responsable inválido en el pendiente {p[0]}: {p[6]}")

    for codigo, (estado, _nota) in d.AVANCE.items():
        if codigo not in pend:
            sys.exit(f"AVANCE menciona un pendiente inexistente: {codigo}")
        if estado not in ("hecho", "parcial"):
            sys.exit(f"AVANCE de {codigo}: estado inválido '{estado}'")

    cubiertos = {}
    for t in d.TAREAS_TECNICAS:
        if t["story"] not in d.TECH_STORIES:
            sys.exit(f"{t['codigo']}: historia técnica desconocida {t['story']}")
        for c in t["cubre"]:
            if c not in pend:
                sys.exit(f"{t['codigo']} cubre un pendiente inexistente: {c}")
            if c in cubiertos:
                sys.exit(f"El pendiente {c} lo cubren dos tareas: {cubiertos[c]} y {t['codigo']}")
            cubiertos[c] = t["codigo"]
    for sid in d.TECH_STORIES:
        if not any(t["story"] == sid for t in d.TAREAS_TECNICAS):
            sys.exit(f"La historia técnica {sid} no tiene tareas")

    for c in d.CAMBIOS:
        if c["padre"] not in d.STORY_IDS and c["padre"] not in d.TECH_STORIES:
            sys.exit(f"Padre desconocido en {c['codigo']}: {c['padre']}")
        if estado_lote(c["lote"]) == "subido":
            sys.exit(f"{c['codigo']}: el lote {c['lote']} ya se subió a Azure; usa un lote nuevo.")
    for hu in d.HUS:
        if hu["hu"] not in d.STORY_IDS:
            sys.exit(f"HU sin ID de Azure: {hu['hu']}")
        if hu["hu"] not in d.COMPONENTES:
            sys.exit(f"HU sin componente en COMPONENTES: {hu['hu']}")
        if hu["pts"] not in d.HORAS_POR_PTS:
            sys.exit(f"Puntos sin plantilla de horas: {hu['hu']}")
    return cubiertos


def filas_csv(it):
    filas = []
    padre = fila(**{"Work Item Type": "User Story", "Title 1": it.titulo})
    if it.existing_id:
        padre["ID"] = it.existing_id
    else:
        padre["State"] = "New"
        padre["Description"] = h(it.desc)
        padre["Tags"] = it.tags
        if it.puntos != "":
            padre["Story Points"] = it.puntos
        if it.prioridad != "":
            padre["Priority"] = it.prioridad
    filas.append(padre)
    for t in it.tareas:
        cerrada = t["estado"] == "Closed"
        filas.append(fila(**{
            "Work Item Type": "Task", "Title 2": f"[{t['codigo']}] {t['titulo']}",
            "Description": h(t["desc"]), "Assigned To": asignado(t["owner"]), "State": t["estado"],
            "Tags": t["tags"], "Activity": t["activity"], "Original Estimate": t["horas"],
            "Remaining Work": 0 if cerrada else t["horas"], "Completed Work": t["horas"] if cerrada else "",
            "Priority": t["prio"]}))
    return filas


def main():
    items = construir()
    cubiertos = validar(items)
    os.makedirs(SALIDA_LOTES, exist_ok=True)
    for f in os.listdir(SALIDA_LOTES):
        if f.startswith("azure-import_") and f.endswith(".csv"):
            os.remove(os.path.join(SALIDA_LOTES, f))

    lotes = OrderedDict((k, []) for k in sorted(set(d.ESTADO_LOTES) | {i.lote for i in items}))
    for it in items:
        lotes[it.lote].append(it)

    columnas = list(COLUMNAS)
    if not d.ITERATION_PATH:
        columnas.remove("Iteration Path")
    if not d.AREA_PATH:
        columnas.remove("Area Path")

    resumen = []
    for lote, its in lotes.items():
        if not its:
            continue
        nombre = f"azure-import_{lote}_{nombre_lote(lote)}.csv"
        with open(os.path.join(SALIDA_LOTES, nombre), "w", newline="", encoding="utf-8-sig") as fh:
            w = csv.DictWriter(fh, fieldnames=columnas, extrasaction="ignore")
            w.writeheader()
            for it in its:
                for r in filas_csv(it):
                    if d.ITERATION_PATH:
                        r["Iteration Path"] = d.ITERATION_PATH
                    if d.AREA_PATH:
                        r["Area Path"] = d.AREA_PATH
                    w.writerow(r)
        nt = sum(len(i.tareas) for i in its)
        hrs = sum(t["horas"] for i in its for t in i.tareas)
        resumen.append((lote, nombre, estado_lote(lote), len(its), nt, hrs))

    escribir_md(items, resumen, cubiertos)
    escribir_trazabilidad()
    print("Lotes generados en", SALIDA_LOTES)
    for r in resumen:
        print(f"  {r[0]}  {r[2]:9s}  {r[3]:2d} historias  {r[4]:3d} tareas  {r[5]:4d} h  {r[1]}")
    print(f"Checklist interno (no se sube): {len(d.PENDIENTES)} pendientes, {sum(p[5] for p in d.PENDIENTES)} h")
    print("Vistas legibles: tareas-sprint-2.md, trazabilidad-sprint-2.md")


def escribir_trazabilidad():
    """Matriz HU -> Regla -> API -> Componente -> Tabla -> Prueba (exigida por el plan de Arquisoft / DiaADia)."""
    L = ["# Trazabilidad del Sprint 2: HU → Regla → API → Componente → Tabla → Prueba\n",
         "> **Generado automáticamente** por `generar.py` desde `backlog_data.py` (HUS). Los endpoints son una "
         "**propuesta a validar en el Día 1**; los IDs `CP-*` son provisionales hasta que Calidad asigne los definitivos.\n",
         "| HU | Pts | Tier | Módulo (paquete) | API propuesta | Tabla(s) | Reglas | Casos |\n|---|---:|---:|---|---|---|---:|---:|"]
    for hu in sorted(d.HUS, key=lambda x: (x["tier"], x["hu"])):
        tabla = hu["tablas"].split(" (")[0] if not hu["tablas"].startswith("(") else "—"
        api = "<br>".join(f"`{e.split('  ')[0]}`" for e in hu["endpoints"])
        L.append(f"| {hu['hu']} | {hu['pts']} | {hu['tier']} | {d.MODULO.get(hu['hu'], '')} | {api} | {tabla} | {len(hu['reglas'])} | {len(hu['pruebas'])} |")
    L.append("")
    for hu in sorted(d.HUS, key=lambda x: (x["tier"], x["hu"])):
        L.append(f"## {d.STORY_TITLES[hu['hu']]} (Azure {d.STORY_IDS[hu['hu']]})\n")
        L.append(f"- **Módulo:** {d.MODULO.get(hu['hu'], '')} · **Responsable:** {d.ROL_TXT[hu['owner']]} · **Depende de:** {hu['deps']}")
        L.append(f"- **Tablas/datos:** {hu['tablas']}")
        L.append("- **API propuesta:** " + "; ".join(f"`{e}`" for e in hu["endpoints"]))
        L.append("- **Reglas de negocio:**")
        L.extend(f"  - {r}" for r in hu["reglas"])
        L.append("- **Casos de prueba:**")
        L.extend(f"  - {p}" for p in hu["pruebas"])
        L.append("")
    with open(os.path.join(AQUI, "trazabilidad-sprint-2.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(L) + "\n")


def estado_sugerido(tarea):
    """Estado sugerido en Azure según el avance de los pendientes internos que agrupa la tarea."""
    if not tarea["cubre"]:
        return ""
    hechos = sum(1 for c in tarea["cubre"] if d.AVANCE.get(c, ("", ""))[0] == "hecho")
    parciales = sum(1 for c in tarea["cubre"] if d.AVANCE.get(c, ("", ""))[0] == "parcial")
    if hechos == len(tarea["cubre"]):
        return "Closed (tras la revisión del PR)"
    if hechos or parciales:
        return f"Active ({hechos}/{len(tarea['cubre'])} ítems hechos" + (f", {parciales} parcial" if parciales else "") + ")"
    return "New"


def escribir_md(items, resumen, cubiertos):
    azure_hu = [t for i in items if i.meta.get("hu") for t in i.tareas]
    azure_tec = [t for i in items if i.meta.get("tech") for t in i.tareas]
    azure_chg = [t for i in items if i.meta.get("cambio") for t in i.tareas]
    h_hu = sum(t["horas"] for t in azure_hu)
    h_tec = sum(t["horas"] for t in azure_tec)
    h_chg = sum(t["horas"] for t in azure_chg if t["estado"] != "Closed")
    h_pend = sum(p[5] for p in d.PENDIENTES)
    hechos = [p for p in d.PENDIENTES if d.AVANCE.get(p[0], ("",))[0] == "hecho"]
    parciales = [p for p in d.PENDIENTES if d.AVANCE.get(p[0], ("",))[0] == "parcial"]
    h_hechos = sum(p[5] for p in hechos)

    L = []
    L.append("# Tareas de Azure del Sprint 2 — por componente\n")
    L.append("> **Generado automáticamente** por `generar.py` desde `backlog_data.py`. No editar a mano: "
             "edita los datos y vuelve a correr `python docs/sprint-2/azure-boards/generar.py`. Proceso Agile de Azure (User Story / Task).\n")
    L.append("**Política (2026-10-07):** Azure lleva pocas tareas, de nivel arquitectónico: una por componente o parte "
             "desarrollada de la aplicación. Las correcciones, bugs, pruebas, documentación suelta, estudio e higiene **no** "
             "generan tarea (ver `README.md`); su detalle vive en el checklist interno de la última sección.\n")

    L.append("## Resumen\n")
    L.append("| Concepto | Tareas | Horas |\n|---|---:|---:|")
    L.append(f"| Azure · tareas de las HU del sprint | {len(azure_hu)} | {h_hu} |")
    L.append(f"| Azure · tareas técnicas (3 historias técnicas) | {len(azure_tec)} | {h_tec} |")
    if azure_chg:
        L.append(f"| Azure · componentes nuevos (`chg`) | {len(azure_chg)} | {h_chg} |")
    L.append(f"| **Total que se sube a Azure** | **{len(azure_hu) + len(azure_tec) + len(azure_chg)}** | **{h_hu + h_tec + h_chg}** |")
    L.append(f"| Checklist interno (NO se sube; {sum(1 for p in d.PENDIENTES if p[0] in cubiertos)} de sus {len(d.PENDIENTES)} ítems "
             f"quedan agrupados en las tareas técnicas) | {len(d.PENDIENTES)} | {h_pend} |")
    L.append(f"| **Trabajo nominal total** (HU + checklist interno; base de la capacidad del plan) | | **{h_hu + h_pend}** |")
    L.append(f"| &nbsp;&nbsp;↳ ya hecho del checklist ({len(hechos)} ítems"
             + (f", {len(parciales)} parciales sin contar" if parciales else "") + ") | | " + f"{h_hechos} |")
    L.append(f"| &nbsp;&nbsp;↳ **por hacer** (HU + checklist pendiente) | | **{h_hu + h_pend - h_hechos}** |\n")

    L.append("## Lotes (un CSV por lote; un lote ya subido NO se vuelve a importar)\n")
    L.append("| Lote | Archivo | Estado | Historias | Tareas | Horas |\n|---|---|---|---:|---:|---:|")
    for lote, nombre, est, np_, nt, hrs in resumen:
        L.append(f"| {lote} | `lotes/{nombre}` | {est} | {np_} | {nt} | {hrs} |")
    L.append("")

    # Horas nominales por responsable: HU + checklist interno (todo lo planificado) y lo que falta
    plan_owner = defaultdict(int)
    falta_owner = defaultdict(int)
    for t in azure_hu:
        plan_owner[t["owner"]] += t["horas"]
        falta_owner[t["owner"]] += t["horas"]
    for p in d.PENDIENTES:
        plan_owner[p[6]] += p[5]
        if d.AVANCE.get(p[0], ("",))[0] != "hecho":
            falta_owner[p[6]] += p[5]
    for t in azure_chg:
        if t["estado"] != "Closed":
            plan_owner[t["owner"]] += t["horas"]
            falta_owner[t["owner"]] += t["horas"]
    por_tier = defaultdict(int)
    for i in items:
        if i.meta.get("tier"):
            por_tier[i.meta["tier"]] += sum(t["horas"] for t in i.tareas)
    L.append("## Esfuerzo nominal por responsable (HU + checklist interno)\n")
    L.append("| Responsable | Planificado (h) | Por hacer (h) |\n|---|---:|---:|")
    for k in ["A", "B", "C", "BD", "QA"]:
        if k in plan_owner:
            L.append(f"| {d.ROL_TXT[k]} | {plan_owner[k]} | {falta_owner[k]} |")
    L.append("")
    L.append("Tareas de HU: Tier 1 = %d h, Tier 2 = %d h (estimación nominal, sin asistente de IA).\n" % (por_tier[1], por_tier[2]))

    for lote in sorted(lote for lote in d.ESTADO_LOTES if any(i.lote == lote for i in items)):
        its = [i for i in items if i.lote == lote]
        L.append(f"## {lote} · {nombre_lote(lote)} ({estado_lote(lote)})\n")
        for it in its:
            enc = it.titulo
            if it.existing_id:
                enc += f" — ID Azure {it.existing_id}"
            L.append(f"### {enc}\n")
            if it.meta.get("hu"):
                m = it.meta
                L.append(f"{m['pts']} pts · Tier {m['tier']} · {d.ROL_TXT[m['owner']]} · depende de: {m['deps']}\n")
                L.append("| Código | Tarea | Actividad | Estado | h | Prio | Resp. |\n|---|---|---|---|---:|---:|---|")
                for t in it.tareas:
                    L.append(f"| {t['codigo']} | {t['titulo']} | {t['activity']} | {t['estado']} | {t['horas']} | {t['prio']} | {t['owner']} |")
            else:
                if it.desc:
                    L.append(f"{it.desc}\n")
                L.append("| Código | Tarea | Actividad | Estado | h | Prio | Resp. | Avance / estado sugerido en Azure |\n|---|---|---|---|---:|---:|---|---|")
                for t in it.tareas:
                    L.append(f"| {t['codigo']} | {t['titulo']} | {t['activity']} | {t['estado']} | {t['horas']} | {t['prio']} | {t['owner']} | {estado_sugerido(t)} |")
            L.append("")

    L.append("## Checklist interno — pendientes de detalle (NO se suben a Azure)\n")
    L.append("Códigos que citan los demás documentos del sprint. La columna *Azure* indica la tarea técnica que agrupa el ítem; "
             "`—` = se hace sin tarea propia (corrección, higiene, documentación, estudio o extensión opcional). "
             "La columna *Avance* (✔ hecho, ◐ parcial) se actualiza en `AVANCE` (`backlog_data.py`).\n")
    L.append("| Código | Área | Pendiente | Actividad | h | Prio | Resp. | Azure | Avance |\n|---|---|---|---|---:|---:|---|---|---|")
    for p in d.PENDIENTES:
        estado, nota = d.AVANCE.get(p[0], ("", ""))
        marca = {"hecho": "✔ ", "parcial": "◐ "}.get(estado, "")
        L.append(f"| {p[0]} | {p[1]} | {p[3]} | {p[4]} | {p[5]} | {p[7]} | {p[6]} | {cubiertos.get(p[0], '—')} | {marca}{nota} |")
    L.append("")
    with open(os.path.join(AQUI, "tareas-sprint-2.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(L) + "\n")


if __name__ == "__main__":
    main()
