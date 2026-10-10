# -*- coding: utf-8 -*-
"""
Verifica la regla de ADR-003 / ADR-007 sobre el código: ningún módulo importa entidades (`domain`) ni
repositorios (`infrastructure`) de otro módulo, y el grafo de dependencias entre módulos no tiene ciclos.

    python docs/arquitectura/verificar_dependencias.py

Muestra, por módulo, de quién depende y por qué capa; lista las excepciones conocidas (ADR-007 §3) y devuelve
código de salida 1 si aparece una dependencia NUEVA fuera de esas excepciones o un ciclo. Pensado para correrlo
a mano o en el pipeline.
"""
import collections
import os
import re
import sys

RAIZ = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SRC = os.path.join(RAIZ, "reservas-backend", "src", "main", "java", "com", "codefactory", "reservas_backend")
MODULOS = ["identity", "provider", "service", "resource", "reservation", "audit", "common"]
NEGOCIO = ["identity", "provider", "service", "resource", "reservation"]

# Excepciones documentadas en ADR-007 §3 (módulo que importa -> lo que importa de otro módulo).
CONOCIDAS = {
    ("common", r"^(identity|provider|service|resource|reservation)\.(domain|infrastructure)\.\w*(Exception)$"),
    ("provider", r"^identity\.(domain\.(DuplicateEmailException|DuplicatePhoneException|RoleName)|infrastructure\.(RegistrationRateLimiter|TooManyRequestsException))$"),
    ("reservation", r"^service\.domain\.ServiceNotAvailableException$"),
    ("*", r"^audit\.domain\.AuditEventType$"),
    ("common", r"^audit\.domain\.AuditEventType$"),
}


def es_conocida(modulo, destino):
    return any(m in ("*", modulo) and re.match(p, destino) for m, p in CONOCIDAS)


def main():
    dep = collections.defaultdict(lambda: collections.defaultdict(set))
    nuevas = []
    for raiz, _, archivos in os.walk(SRC):
        for f in archivos:
            if not f.endswith(".java"):
                continue
            ruta = os.path.join(raiz, f)
            modulo = os.path.relpath(ruta, SRC).split(os.sep)[0]
            if modulo not in MODULOS:
                continue
            texto = open(ruta, encoding="utf-8").read()
            for mod, capa, clase in re.findall(r"^import com\.codefactory\.reservas_backend\.(\w+)\.(\w+)\.(\w+)", texto, re.M):
                if mod == modulo or mod not in MODULOS:
                    continue
                dep[modulo][mod].add(capa)
                destino = "%s.%s.%s" % (mod, capa, clase)
                if capa in ("domain", "infrastructure") and mod != "common" and not es_conocida(modulo, destino):
                    nuevas.append((modulo, f, destino))

    print("Dependencias entre módulos (de qué capa importa):")
    for m in MODULOS:
        print("  %-12s -> %s" % (m, ", ".join("%s[%s]" % (k, "/".join(sorted(v))) for k, v in sorted(dep[m].items())) or "—"))

    # Ciclos solo entre módulos de negocio (common recibe excepciones de todos por diseño, ver ADR-007 §3).
    grafo = {m: {d for d in dep[m] if d in NEGOCIO} for m in NEGOCIO}
    ciclos = []

    def visita(n, camino):
        for d in grafo[n]:
            if d in camino:
                ciclos.append(camino[camino.index(d):] + [d])
            else:
                visita(d, camino + [d])

    for m in NEGOCIO:
        visita(m, [m])

    ok = True
    if ciclos:
        ok = False
        print("\nCICLOS entre módulos de negocio:")
        for c in ciclos:
            print("  " + " -> ".join(c))
    else:
        print("\nSin ciclos entre módulos de negocio.")
    if nuevas:
        ok = False
        print("\nDependencias NUEVAS de entidades o repositorios ajenos (no están en ADR-007 §3):")
        for modulo, f, destino in sorted(set(nuevas)):
            print("  %s importa %s (en %s)" % (modulo, destino, f))
    else:
        print("Ninguna importación de entidades o repositorios ajenos fuera de las excepciones documentadas.")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
