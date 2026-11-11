# -*- coding: utf-8 -*-
"""
Genera el diagrama de componentes LEYENDO EL CÓDIGO (no se dibuja a mano, así no se desactualiza):

    python docs/arquitectura/diagramas/generar_diagrama_componentes.py

- Componente = módulo del monolito (paquete de primer nivel).
- Interfaz provista (bola) = una `interface` pública de la capa `application` de un módulo que OTRO módulo importa,
  más el evento de dominio `UserDeletionRequested`.
- Interfaz requerida (media luna) = esa misma interfaz en el módulo que la importa.
- Cada interfaz tiene un carril vertical que une su bola (quien la provee) con las medias lunas (quienes la usan).
  Caso especial: `BusyTimeSource` la DECLARA service pero la IMPLEMENTA reservation (inversión de dependencias,
  ADR-007 D5): la bola va en reservation y la media luna en service.

Salida: diagrama-componentes.svg y .png (PyMuPDF) y conexiones-diagrama-componentes.md (tabla de respaldo).
"""
import collections
import os
import re

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.abspath(os.path.join(AQUI, "..", "..", ".."))
SRC = os.path.join(RAIZ, "reservas-backend", "src", "main", "java", "com", "codefactory", "reservas_backend")

# Filas de arriba (quien más depende) hacia abajo (quien menos).
MODULOS = ["notification", "report", "reservation", "service", "resource", "provider", "identity", "audit"]
DESCRIPCION = {
    "notification": "Avisos por correo (Brevo) · ADR-009",
    "report": "Reportes HU-29..34 · solo lectura",
    "reservation": "Reservas HU-22..28 · orquesta cancelaciones",
    "service": "Servicios, catálogo y disponibilidad",
    "resource": "Recursos, horarios y cierres",
    "provider": "Proveedor y negocio",
    "identity": "Usuarios, sesión, roles y MFA",
    "audit": "Registro de eventos",
}
COLOR = {"notification": "#ad1457", "report": "#2e7d32", "reservation": "#6a1b9a", "service": "#ef6c00", "resource": "#00838f",
         "provider": "#1565c0", "identity": "#c62828", "audit": "#5d4037"}
EVENTOS = {"UserDeletionRequested", "BookingsCancelled"}
INVERTIDAS = {"BusyTimeSource": "reservation"}  # interfaz -> módulo que la implementa (provee)


def es_interfaz(modulo, nombre):
    ruta = os.path.join(SRC, modulo, "application", nombre + ".java")
    if not os.path.exists(ruta):
        return False
    texto = open(ruta, encoding="utf-8").read()
    return nombre in EVENTOS or re.search(r"public\s+interface\s+" + nombre + r"\b", texto) is not None


def leer_conexiones():
    """{(interfaz, proveedor)} -> set(consumidores)."""
    usos = collections.defaultdict(set)
    for raiz, _, archivos in os.walk(SRC):
        for f in archivos:
            if not f.endswith(".java"):
                continue
            modulo = os.path.relpath(os.path.join(raiz, f), SRC).split(os.sep)[0]
            if modulo not in MODULOS:
                continue
            texto = open(os.path.join(raiz, f), encoding="utf-8").read()
            for mod, nombre in re.findall(r"^import com\.codefactory\.reservas_backend\.(\w+)\.application\.(\w+);", texto, re.M):
                if mod == modulo or mod not in MODULOS or not es_interfaz(mod, nombre):
                    continue
                if nombre in INVERTIDAS:
                    # service la declara; reservation la implementa: reservation provee y service requiere.
                    if modulo == INVERTIDAS[nombre]:
                        usos[(nombre, modulo)].add(mod)
                    continue
                usos[(nombre, mod)].add(modulo)
    return usos


def svg(usos):
    # Carriles ordenados por la fila del proveedor y luego por nombre.
    carriles = sorted(usos, key=lambda k: (MODULOS.index(k[1]), k[0]))
    x0, paso = 330, 64
    cabecera = 230
    fila_y = {m: cabecera + 40 + i * 92 for i, m in enumerate(MODULOS)}
    ancho = x0 + paso * len(carriles) + 40
    alto = fila_y[MODULOS[-1]] + 110
    caja_x, caja_w, caja_h = 30, 240, 56
    o = []
    o.append('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d" font-family="Arial, Helvetica, sans-serif">' % (ancho, alto, ancho, alto))
    o.append('<rect width="%d" height="%d" fill="#ffffff"/>' % (ancho, alto))
    o.append('<text x="%d" y="30" text-anchor="middle" font-size="20" font-weight="bold" fill="#0d1b2a">Diagrama de componentes — Plataforma de Reservas (entrega final)</text>' % (ancho / 2))
    o.append('<text x="%d" y="50" text-anchor="middle" font-size="12" fill="#546e7a">Generado del código (generar_diagrama_componentes.py). Bola = interfaz provista · media luna = interfaz requerida · cada carril vertical une la bola con quienes la usan.</text>' % (ancho / 2))
    # leyenda
    o.append('<circle cx="40" cy="78" r="7" fill="#fff" stroke="#37474f" stroke-width="2"/><text x="54" y="82" font-size="12" fill="#37474f">provista (bola)</text>')
    o.append('<path d="M 160 71 A 8 8 0 0 0 160 85" fill="none" stroke="#37474f" stroke-width="2.2"/><text x="172" y="82" font-size="12" fill="#37474f">requerida (media luna)</text>')
    o.append('<text x="330" y="82" font-size="12" fill="#37474f">«event» = evento de dominio (UserDeletionRequested, síncrono, HU-28; BookingsCancelled, aviso por correo tras confirmar, ADR-009) · BusyTimeSource: la declara service y la implementa reservation (inversión de dependencias)</text>')

    # alcance horizontal de cada fila (hasta su último carril)
    alcance = {m: 0 for m in MODULOS}
    for i, (nombre, prov) in enumerate(carriles):
        x = x0 + i * paso
        for m in {prov} | usos[(nombre, prov)]:
            alcance[m] = max(alcance[m], x)

    # espinas de cada componente
    for m in MODULOS:
        y = fila_y[m]
        if alcance[m]:
            o.append('<line x1="%d" y1="%d" x2="%d" y2="%d" stroke="#b0bec5" stroke-width="1.5"/>' % (caja_x + caja_w, y, alcance[m], y))

    # carriles, etiquetas, bolas y medias lunas
    for i, (nombre, prov) in enumerate(carriles):
        x = x0 + i * paso
        color = COLOR[prov]
        ys = [fila_y[prov]] + [fila_y[c] for c in usos[(nombre, prov)]]
        etiqueta = ("«event» " if nombre in EVENTOS else "") + nombre
        o.append('<text x="%d" y="%d" font-size="12" font-weight="bold" fill="%s" transform="rotate(-60 %d %d)">%s</text>' % (x + 4, cabecera - 6, color, x + 4, cabecera - 6, etiqueta))
        o.append('<line x1="%d" y1="%d" x2="%d" y2="%d" stroke="%s" stroke-width="2" stroke-dasharray="1,0"/>' % (x, cabecera, x, max(ys), color))
        # bola del proveedor
        yp = fila_y[prov]
        o.append('<circle cx="%d" cy="%d" r="8" fill="#ffffff" stroke="%s" stroke-width="2.5"/>' % (x, yp, color))
        for c in sorted(usos[(nombre, prov)], key=MODULOS.index):
            yc = fila_y[c]
            # media luna abierta hacia la bola (arriba o abajo)
            if yc < yp:
                o.append('<path d="M %d %d A 11 11 0 0 0 %d %d" fill="#ffffff" stroke="%s" stroke-width="2.5"/>' % (x - 11, yc - 2, x + 11, yc - 2, color))
            else:
                o.append('<path d="M %d %d A 11 11 0 0 1 %d %d" fill="#ffffff" stroke="%s" stroke-width="2.5"/>' % (x - 11, yc + 2, x + 11, yc + 2, color))
            o.append('<circle cx="%d" cy="%d" r="2.5" fill="%s"/>' % (x, yc, color))

    # cajas de componentes (encima de todo)
    for m in MODULOS:
        y = fila_y[m]
        color = COLOR[m]
        o.append('<rect x="%d" y="%d" width="%d" height="%d" rx="5" fill="#ffffff" stroke="%s" stroke-width="2.2"/>' % (caja_x, y - caja_h / 2, caja_w, caja_h, color))
        o.append('<rect x="%d" y="%d" width="14" height="10" fill="#fff" stroke="%s"/><rect x="%d" y="%d" width="6" height="3" fill="#fff" stroke="%s"/><rect x="%d" y="%d" width="6" height="3" fill="#fff" stroke="%s"/>'
                 % (caja_x + caja_w - 24, y - 22, color, caja_x + caja_w - 27, y - 20, color, caja_x + caja_w - 27, y - 16, color))
        o.append('<text x="%d" y="%d" font-size="11" fill="#546e7a">«component»</text>' % (caja_x + 12, y - 10))
        o.append('<text x="%d" y="%d" font-size="15" font-weight="bold" fill="%s">%s</text>' % (caja_x + 12, y + 8, color, m))
        o.append('<text x="%d" y="%d" font-size="10.5" fill="#455a64">%s</text>' % (caja_x + 12, y + 22, DESCRIPCION[m]))

    o.append('<text x="30" y="%d" font-size="11" fill="#78909c">common (errores, configuración, filtros web) es transversal y no expone interfaces de negocio; no se dibuja. Todos los componentes salvo audit y notification exponen además su API REST (48 endpoints).</text>' % (alto - 20))
    o.append('</svg>')
    return "\n".join(o), carriles


def tabla(usos, carriles):
    L = ["# Conexiones — Diagrama de componentes (generada del código)", "",
         "Respaldo del diagrama [`diagrama-componentes.svg`](diagrama-componentes.svg). La regenera "
         "`python docs/arquitectura/diagramas/generar_diagrama_componentes.py`; no editar a mano.", "",
         "| Interfaz | La provee | La requieren |", "|---|---|---|"]
    for nombre, prov in carriles:
        L.append("| `%s` | %s | %s |" % (nombre, prov, ", ".join(sorted(usos[(nombre, prov)], key=MODULOS.index))))
    L.append("")
    return "\n".join(L)


def main():
    usos = leer_conexiones()
    texto, carriles = svg(usos)
    destino = os.path.join(AQUI, "diagrama-componentes.svg")
    open(destino, "w", encoding="utf-8").write(texto)
    open(os.path.join(AQUI, "conexiones-diagrama-componentes.md"), "w", encoding="utf-8").write(tabla(usos, carriles))
    try:
        import pymupdf
        doc = pymupdf.open(destino)
        doc[0].get_pixmap(matrix=pymupdf.Matrix(1.5, 1.5)).save(os.path.join(AQUI, "diagrama-componentes.png"))
    except ImportError:
        print("Sin PyMuPDF: solo se generó el SVG")
    print("Componentes: %d interfaces, %d conexiones" % (len(carriles), sum(len(v) for v in usos.values())))


if __name__ == "__main__":
    main()
