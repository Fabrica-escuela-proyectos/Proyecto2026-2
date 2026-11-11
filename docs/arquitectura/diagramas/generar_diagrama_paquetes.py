# -*- coding: utf-8 -*-
"""
Genera el diagrama de paquetes LEYENDO EL CÓDIGO:

    python docs/arquitectura/diagramas/generar_diagrama_paquetes.py

- Un paquete por módulo, con las capas que de verdad tiene (controller, application, infrastructure, domain) y
  cuántas clases hay en cada una.
- Flecha = dependencia: del módulo que importa al importado (convención UML del curso). Los módulos
  van de izquierda a derecha según el grafo (ninguna flecha va hacia la izquierda: no hay ciclos).
- audit y common son transversales: se dibujan como una franja inferior a la que bajan los módulos que los usan.

Salida: diagrama-paquetes.svg y .png. La fuente PlantUML anterior (diagrama-paquetes.puml) queda como histórico.
"""
import collections
import os
import re

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.abspath(os.path.join(AQUI, "..", "..", ".."))
SRC = os.path.join(RAIZ, "reservas-backend", "src", "main", "java", "com", "codefactory", "reservas_backend")

NEGOCIO = ["notification", "report", "reservation", "service", "resource", "provider", "identity"]
TRANSVERSALES = ["audit", "common"]
CAPAS = ["controller", "application", "infrastructure", "domain"]
COLOR = {"notification": "#ad1457", "report": "#2e7d32", "reservation": "#6a1b9a", "service": "#ef6c00", "resource": "#00838f",
         "provider": "#1565c0", "identity": "#c62828", "audit": "#5d4037", "common": "#546e7a"}


def leer():
    clases = collections.defaultdict(collections.Counter)
    deps = collections.defaultdict(set)
    for raiz, _, archivos in os.walk(SRC):
        for f in archivos:
            if not f.endswith(".java"):
                continue
            partes = os.path.relpath(os.path.join(raiz, f), SRC).split(os.sep)
            if len(partes) < 3:
                continue
            modulo, capa = partes[0], partes[1]
            clases[modulo][capa] += 1
            texto = open(os.path.join(raiz, f), encoding="utf-8").read()
            for mod in re.findall(r"^import com\.codefactory\.reservas_backend\.(\w+)\.", texto, re.M):
                if mod != modulo:
                    deps[modulo].add(mod)
    return clases, deps


def main():
    clases, deps = leer()
    ancho_p, sep = 190, 34
    x0, y_paq = 40, 330
    xs = {m: x0 + i * (ancho_p + sep) for i, m in enumerate(NEGOCIO)}
    ancho = x0 + len(NEGOCIO) * (ancho_p + sep) + 10
    alto_capa, gap = 46, 14
    alto_p = 40 + len(CAPAS) * (alto_capa + gap)
    y_bus = y_paq + alto_p + 70
    alto = y_bus + 150
    o = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d" font-family="Arial, Helvetica, sans-serif">' % (ancho, alto, ancho, alto),
         '<rect width="%d" height="%d" fill="#ffffff"/>' % (ancho, alto),
         '<text x="%d" y="30" text-anchor="middle" font-size="20" font-weight="bold" fill="#0d1b2a">Diagrama de paquetes — monolito modular (entrega final)</text>' % (ancho / 2),
         '<text x="%d" y="50" text-anchor="middle" font-size="12" fill="#546e7a">Generado del código (generar_diagrama_paquetes.py). Flecha = «importa a» (del módulo dependiente al módulo del que depende). Entre módulos solo se importa la capa application (ADR-003/007).</text>' % (ancho / 2)]

    # Arcos de dependencia entre módulos de negocio (todos van hacia la derecha).
    aristas = [(a, b) for a in NEGOCIO for b in NEGOCIO if b in deps[a]]
    for a, b in sorted(aristas, key=lambda e: NEGOCIO.index(e[1]) - NEGOCIO.index(e[0])):
        dist = NEGOCIO.index(b) - NEGOCIO.index(a)
        xa = xs[a] + ancho_p * 0.62 + (NEGOCIO.index(b) * 6)
        xb = xs[b] + ancho_p * 0.38 - (NEGOCIO.index(a) * 6)
        h = 40 + dist * 42
        color = COLOR[a]
        o.append('<path d="M %.0f %d C %.0f %d, %.0f %d, %.0f %d" fill="none" stroke="%s" stroke-width="1.8" stroke-dasharray="6,4"/>'
                 % (xa, y_paq, xa, y_paq - h, xb, y_paq - h, xb, y_paq - 6, color))
        o.append('<polygon points="%.0f,%d %.0f,%d %.0f,%d" fill="%s"/>' % (xb, y_paq, xb - 5, y_paq - 11, xb + 5, y_paq - 11, color))

    # Paquetes con sus capas.
    for m in NEGOCIO:
        x = xs[m]
        color = COLOR[m]
        o.append('<rect x="%d" y="%d" width="70" height="18" fill="#ffffff" stroke="%s" stroke-width="2"/>' % (x, y_paq - 18 + 0, color))
        o.append('<rect x="%d" y="%d" width="%d" height="%d" fill="#fafcff" stroke="%s" stroke-width="2"/>' % (x, y_paq, ancho_p, alto_p, color))
        o.append('<text x="%d" y="%d" font-size="15" font-weight="bold" fill="%s">%s</text>' % (x + 10, y_paq + 24, color, m))
        presentes = [c for c in CAPAS if clases[m][c]]
        for i, capa in enumerate(CAPAS):
            y = y_paq + 40 + i * (alto_capa + gap)
            existe = capa in presentes
            o.append('<rect x="%d" y="%d" width="%d" height="%d" rx="3" fill="%s" stroke="%s" %s/>'
                     % (x + 12, y, ancho_p - 24, alto_capa, "#ffffff" if existe else "#f5f5f5", color if existe else "#bdbdbd",
                        "" if existe else 'stroke-dasharray="4,3"'))
            o.append('<text x="%d" y="%d" font-size="12.5" font-weight="bold" fill="%s">%s.%s</text>' % (x + 22, y + 19, "#263238" if existe else "#9e9e9e", m, capa))
            o.append('<text x="%d" y="%d" font-size="11" fill="#607d8b">%s</text>' % (x + 22, y + 36, ("%d clase%s" % (clases[m][capa], "" if clases[m][capa] == 1 else "s")) if existe else "no tiene (no la necesita)"))
            if i < len(CAPAS) - 1 and existe:
                o.append('<line x1="%d" y1="%d" x2="%d" y2="%d" stroke="#90a4ae" stroke-width="1.2" stroke-dasharray="3,3"/>' % (x + ancho_p / 2, y + alto_capa, x + ancho_p / 2, y + alto_capa + gap))

    # Franja de transversales.
    o.append('<rect x="%d" y="%d" width="%d" height="96" rx="6" fill="#f5f5f5" stroke="#78909c" stroke-width="1.6"/>' % (x0, y_bus, ancho - x0 - 20))
    o.append('<text x="%d" y="%d" font-size="13" font-weight="bold" fill="#37474f">Transversales (los usan todos los módulos)</text>' % (x0 + 14, y_bus + 22))
    o.append('<text x="%d" y="%d" font-size="12" fill="%s"><tspan font-weight="bold">audit</tspan> — AuditService, AuditLog (%d clases): eventos de seguridad y de negocio</text>' % (x0 + 14, y_bus + 48, COLOR["audit"], sum(clases["audit"].values())))
    o.append('<text x="%d" y="%d" font-size="12" fill="%s"><tspan font-weight="bold">common</tspan> — config, error, security, validation, web (%d clases): formato de error único, filtros, reloj de Bogotá, Swagger</text>' % (x0 + 14, y_bus + 70, COLOR["common"], sum(clases["common"].values())))
    for m in NEGOCIO:
        if deps[m] & set(TRANSVERSALES):
            xc = xs[m] + ancho_p / 2
            o.append('<line x1="%.0f" y1="%d" x2="%.0f" y2="%d" stroke="#78909c" stroke-width="1.5" stroke-dasharray="6,4"/>' % (xc, y_paq + alto_p, xc, y_bus - 6))
            o.append('<polygon points="%.0f,%d %.0f,%d %.0f,%d" fill="#78909c"/>' % (xc, y_bus, xc - 5, y_bus - 11, xc + 5, y_bus - 11))

    o.append('<text x="%d" y="%d" font-size="11" fill="#78909c">Dentro de cada módulo: controller → application → infrastructure → domain. report no tiene infrastructure: no guarda nada, lee por las interfaces de reservation, resource, service y provider (ADR-008).</text>' % (x0, alto - 18))
    o.append('</svg>')
    destino = os.path.join(AQUI, "diagrama-paquetes.svg")
    open(destino, "w", encoding="utf-8").write("\n".join(o))
    try:
        import pymupdf
        pymupdf.open(destino)[0].get_pixmap(matrix=pymupdf.Matrix(1.5, 1.5)).save(os.path.join(AQUI, "diagrama-paquetes.png"))
    except ImportError:
        print("Sin PyMuPDF: solo se generó el SVG")
    print("Paquetes: %d módulos, %d dependencias entre módulos de negocio" % (len(NEGOCIO), len(aristas)))


if __name__ == "__main__":
    main()
