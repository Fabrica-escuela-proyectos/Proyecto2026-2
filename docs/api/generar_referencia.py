# -*- coding: utf-8 -*-
"""
Genera docs/api/referencia-api-sprint-2.md leyendo el CÓDIGO: matriz de endpoints con su control de acceso y catálogo
de DTO con tipos y validaciones. Como sale del código, no se puede desfasar de lo implementado.

    python docs/api/generar_referencia.py

Qué lee:
  - *Controller.java        -> método HTTP, ruta, @PreAuthorize
  - SecurityConfig.java     -> rutas públicas (permitAll)
  - controller/dto/*.java   -> DTO de petición y respuesta (records y clases con Lombok), más los DTO anidados
    en controladores y las respuestas definidas como record dentro de un servicio (lista EXTRA_DTO_FILES)
"""
import glob
import os
import re

RAIZ = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SRC = os.path.join(RAIZ, "reservas-backend", "src", "main", "java", "com", "codefactory", "reservas_backend")
SALIDA = os.path.join(RAIZ, "docs", "api", "referencia-api-sprint-2.md")

EXTRA_DTO_FILES = [
    os.path.join(SRC, "common", "error", "ApiError.java"),
    os.path.join(SRC, "reservation", "controller", "ResourceDeactivationController.java"),
    os.path.join(SRC, "reservation", "application", "ResourceDeactivationService.java"),
]

# HU a la que pertenece cada endpoint (clave: método + ruta con las variables de ruta como {x}).
HU_POR_RUTA = [
    (r"POST /api/v1/users$", "HU-01"), (r"POST /api/v1/auth/login", "HU-02"), (r"POST /api/v1/providers$", "HU-03"),
    (r"POST /api/v1/auth/logout", "HU-04"), (r"/api/v1/auth/mfa", "HU-02/05 (ADR-004)"),
    (r"(PATCH|DELETE|GET) /api/v1/users/\{[^}]+\}(/role)?$", "HU-05/06"), (r"GET /api/v1/providers/", "HU-06"),
    (r"booking-lead-time", "HU-08"), (r"(GET|POST) /api/v1/businesses/\{[^}]+\}/services", "HU-09"),
    (r"GET /api/v1/businesses(/\{[^}]+\})?$", "HU-13"), (r"/api/v1/businesses/\{[^}]+\}/resources", "HU-14"),
    (r"deactivation", "HU-16"), (r"reactivation", "HU-17"), (r"/api/v1/services/\{[^}]+\}/resources", "HU-18"),
    (r"/api/v1/resources/\{[^}]+\}/availability", "HU-19"), (r"/api/v1/services/\{[^}]+\}/availability", "HU-20"),
    (r"POST /api/v1/bookings$", "HU-22"), (r"GET /api/v1/(bookings/me|users/\{[^}]+\}/bookings)", "HU-23"),
    (r"GET /api/v1/businesses/\{[^}]+\}/bookings", "HU-24"), (r"/cancellation$", "HU-25"), (r"provider-cancellation", "HU-26"),
]


def leer(ruta):
    with open(ruta, encoding="utf-8") as f:
        return f.read()


# ------------------------------------------------------------------------------------------------ endpoints

def rutas_publicas():
    t = leer(os.path.join(SRC, "identity", "infrastructure", "SecurityConfig.java"))
    publicas = []
    for m in re.finditer(r'requestMatchers\((?:HttpMethod\.(\w+),\s*)?"([^"]+)"\)\.permitAll\(\)', t):
        publicas.append((m.group(1) or "*", m.group(2)))
    return publicas


def coincide(patron, ruta):
    rx = "^" + re.escape(patron).replace(r"\*", "[^/]+") + "$"
    return re.match(rx, re.sub(r"\{[^}]+\}", "x", ruta)) is not None


def balanceado(texto, ini, abre="(", cierra=")"):
    """Devuelve el índice del cierre que corresponde al delimitador de apertura en ini."""
    nivel, i, en_str = 0, ini, False
    while i < len(texto):
        c = texto[i]
        if c == '"' and texto[i - 1] != "\\":
            en_str = not en_str
        elif not en_str:
            if c == abre:
                nivel += 1
            elif c == cierra:
                nivel -= 1
                if nivel == 0:
                    return i
        i += 1
    return len(texto) - 1


def endpoints():
    publicas = rutas_publicas()
    filas = []
    for ruta in sorted(glob.glob(os.path.join(SRC, "**", "*Controller.java"), recursive=True)):
        t = leer(ruta)
        base = ""
        m = re.search(r'@RequestMapping\(\s*"([^"]*)"\s*\)', t)
        if m:
            base = m.group(1)
        for mm in re.finditer(r'@(Get|Post|Put|Patch|Delete)Mapping(\(\s*"([^"]*)"\s*\))?', t):
            metodo = mm.group(1).upper()
            sub = mm.group(3) or ""
            # anotaciones del mismo método: desde el fin de la anterior firma hasta la firma de este
            ini = t.rfind("}", 0, mm.start())
            tramo = t[ini if ini != -1 else 0: mm.end() + 400]
            rol = re.search(r"@PreAuthorize\(\"([^\"]+)\"\)", t[max(ini, 0): mm.start() + 400])
            path = (base + sub) if not sub.startswith("/api/") else sub
            path = path.replace("//", "/") or "/"
            if sub.startswith("/api/"):
                path = sub
            expr = rol.group(1) if rol else ""
            acceso = "Autenticado (cualquier rol)"
            roles = re.findall(r"hasRole\('(\w+)'\)", expr)
            if roles:
                acceso = "Rol " + " o ".join(roles)
            elif "isAuthenticated" in expr:
                acceso = "Autenticado (cualquier rol)"
            if any((mt in ("*", metodo)) and coincide(pat, path) for mt, pat in publicas):
                acceso = "**Público**"
            hu = next((h for rx, h in HU_POR_RUTA if re.search(rx, "%s %s" % (metodo, path))), "—")
            filas.append((path, metodo, acceso, hu, os.path.basename(ruta).replace(".java", "")))
    filas.sort(key=lambda f: (f[0], f[1]))
    return filas


# ------------------------------------------------------------------------------------------------ DTO

def constantes(texto):
    c = {}
    for m in re.finditer(r'(?:static\s+final\s+)?String\s+([A-Z_]+)\s*=\s*"((?:[^"\\]|\\.)*)"', texto):
        c[m.group(1)] = m.group(2).replace('\\\\', '\\')
    for m in re.finditer(r'(?:static\s+final\s+)?(?:int|long)\s+([A-Z_]+)\s*=\s*([\d_]+)L?', texto):
        c[m.group(1)] = m.group(2).replace("_", "")
    return c


def anotaciones(chunk):
    """Lista de (nombre, argumentos_en_crudo) de las anotaciones de un fragmento."""
    res, i = [], 0
    while True:
        m = re.search(r"@(\w+)", chunk[i:])
        if not m:
            break
        ini = i + m.end()
        args = ""
        if ini < len(chunk) and chunk[ini] == "(":
            fin = balanceado(chunk, ini)
            args = chunk[ini + 1:fin]
            ini = fin + 1
        res.append((m.group(1), args))
        i = ini
    return res


def valor(args, clave, consts):
    m = re.search(r"\b%s\s*=\s*(\"((?:[^\"\\]|\\.)*)\"|[\w.]+)" % clave, args)
    if not m:
        return None
    if m.group(2) is not None:
        return m.group(2).replace('\\\\', '\\')
    return consts.get(m.group(1), m.group(1))


def restricciones(anots, consts):
    reglas, mensajes = [], []
    for nombre, args in anots:
        msg = valor(args, "message", consts)
        if nombre in ("NotBlank", "NotNull", "NotEmpty"):
            reglas.append("obligatorio")
        elif nombre == "Size":
            mn, mx = valor(args, "min", consts), valor(args, "max", consts)
            if mx is None and re.match(r"\s*\d+\s*$", args):
                mx = args.strip()
            reglas.append("tamaño " + (f"≥ {mn} " if mn else "") + (f"≤ {mx}" if mx else ""))
        elif nombre in ("Min", "Max"):
            v = valor(args, "value", consts) or args.split(",")[0].strip()
            v = re.sub(r"[_L]", "", str(v))
            reglas.append(("≥ " if nombre == "Min" else "≤ ") + v)
        elif nombre == "Pattern":
            reglas.append("patrón `%s`" % (valor(args, "regexp", consts) or "").replace("|", "\\|"))
        elif nombre == "Email":
            reglas.append("correo válido")
        elif nombre == "ValidPassword":
            reglas.append("política de contraseña (8+ caracteres, mayúscula, minúscula, carácter especial; ≤ 72 bytes)")
        elif nombre == "ValidPhone":
            reglas.append("celular colombiano `3XXXXXXXXX`")
        elif nombre == "Valid":
            reglas.append("valida el contenido anidado")
        else:
            continue
        if msg:
            mensajes.append(msg)
    return reglas, mensajes


def limpiar_tipo(t):
    t = re.sub(r"@\w+(\((?:[^()\"]|\"[^\"]*\")*\))?\s*", "", t)  # anotaciones dentro de genéricos
    return re.sub(r"\s+", " ", t).strip()


def trocear(texto):
    partes, nivel, actual, en_str = [], 0, "", False
    for i, c in enumerate(texto):
        if c == '"' and (i == 0 or texto[i - 1] != "\\"):
            en_str = not en_str
        if not en_str:
            if c in "(<{":
                nivel += 1
            elif c in ")>}":
                nivel -= 1
            if c == "," and nivel == 0:
                partes.append(actual)
                actual = ""
                continue
        actual += c
    if actual.strip():
        partes.append(actual)
    return partes


def dtos_de(ruta):
    t = leer(ruta)
    consts = constantes(t)
    resultado = []
    for m in re.finditer(r"\b(record|class)\s+(\w+)\s*(\()?", t):
        tipo, nombre = m.group(1), m.group(2)
        if nombre in ("Object",):
            continue
        campos = []
        if tipo == "record" and m.group(3):
            ini = m.end() - 1
            fin = balanceado(t, ini)
            for comp in trocear(t[ini + 1:fin]):
                comp = comp.strip()
                if not comp:
                    continue
                mt = re.match(r"((?:@\w+(?:\((?:[^()\"]|\"[^\"]*\")*\))?\s*)*)(.+?)\s+(\w+)$", comp, re.S)
                if not mt:
                    continue
                anots = anotaciones(mt.group(1))
                # restricciones sobre los elementos de un genérico (List<@NotNull X>)
                anots_internas = anotaciones(re.sub(r"[^<]*<(.*)>[^>]*", r"\1", mt.group(2))) if "<" in mt.group(2) else []
                r1, m1 = restricciones(anots, consts)
                r2, m2 = restricciones(anots_internas, consts)
                reglas = r1 + ["elementos: " + x for x in r2]
                campos.append((mt.group(3), limpiar_tipo(mt.group(2)), reglas, m1 + m2))
        elif tipo == "class":
            ini = t.find("{", m.end())
            if ini == -1:
                continue
            fin = balanceado(t, ini, "{", "}")
            cuerpo = t[ini + 1:fin]
            # quita las clases/records anidados para no mezclar sus campos
            for n in re.finditer(r"\b(?:record|class)\s+\w+\s*(\(|\{)", cuerpo):
                pass
            pendiente = ""
            for linea in cuerpo.split("\n"):
                ls = linea.strip()
                if ls.startswith("@") or (pendiente and not ls.endswith(";") and not ls.startswith("private")):
                    pendiente += " " + ls
                    continue
                mf = re.match(r"private\s+(?:final\s+)?(.+?)\s+(\w+)\s*(?:=.*)?;$", ls)
                if mf:
                    anots = anotaciones(pendiente)
                    anots_internas = anotaciones(re.sub(r"[^<]*<(.*)>[^>]*", r"\1", mf.group(1))) if "<" in mf.group(1) else []
                    r1, m1 = restricciones(anots, consts)
                    r2, m2 = restricciones(anots_internas, consts)
                    campos.append((mf.group(2), limpiar_tipo(mf.group(1)), r1 + ["elementos: " + x for x in r2], m1 + m2))
                if not ls.startswith("@"):
                    pendiente = ""
        if campos:
            resultado.append((nombre, campos))
    return resultado


def catalogo():
    archivos = sorted(glob.glob(os.path.join(SRC, "**", "controller", "dto", "*.java"), recursive=True)) + EXTRA_DTO_FILES
    por_modulo = {}
    for a in archivos:
        modulo = os.path.relpath(a, SRC).split(os.sep)[0]
        for nombre, campos in dtos_de(a):
            por_modulo.setdefault(modulo, []).append((nombre, os.path.basename(a), campos))
    return por_modulo


# ------------------------------------------------------------------------------------------------ salida

def escribir():
    eps = endpoints()
    cat = catalogo()
    L = []
    L.append("# Referencia de la API — Sprint 2 (generada desde el código)\n")
    L.append("> **Generado automáticamente** por `docs/api/generar_referencia.py` leyendo los controladores, `SecurityConfig` y los DTO. "
             "**No editar a mano**: ejecuta `python docs/api/generar_referencia.py`. Los contratos narrados, con ejemplos y reglas de negocio, "
             "están en [endpoints-sprint-2.md](endpoints-sprint-2.md); los códigos de error, en [errores-api-sprint-2.md](errores-api-sprint-2.md); "
             "la guía para verlos en Swagger, en [guia-swagger-openapi.md](guia-swagger-openapi.md).\n")
    roles = {}
    for _, _, acceso, _, _ in eps:
        roles[acceso] = roles.get(acceso, 0) + 1
    L.append("## 1. Matriz de endpoints y control de acceso\n")
    L.append("**%d endpoints.** Resumen: %s. Toda ruta que no es pública exige un token JWT válido con sesión vigente (si no, `401`); "
             "un rol insuficiente da `403`. Además de la matriz, los endpoints de gestión comprueban la **pertenencia** (el recurso es del "
             "proveedor/cliente que llama) en la capa de servicio (`BusinessAccessService`): ver [OWASP A01](../seguridad/owasp-top10-sprint-2.md).\n" %
             (len(eps), "; ".join("%d %s" % (n, a.replace("**", "")) for a, n in sorted(roles.items(), key=lambda x: -x[1]))))
    L.append("| Método | Ruta | Acceso | HU | Controlador |")
    L.append("|---|---|---|---|---|")
    for path, metodo, acceso, hu, ctl in eps:
        L.append("| %s | `%s` | %s | %s | %s |" % (metodo, path, acceso, hu, ctl))
    L.append("\nAdemás, `GET /actuator/health` es público (usado por Render y por el pipeline); los demás endpoints de Actuator no están expuestos.\n")

    L.append("## 2. Catálogo de DTO\n")
    L.append("Tipos: `UUID` se serializa como texto; `Instant` como fecha-hora ISO-8601 en UTC; las horas y fechas de negocio viajan como "
             "texto (`yyyy-MM-dd`, `HH:mm`) en hora de Bogotá. «obligatorio» = `@NotNull`/`@NotBlank`/`@NotEmpty`. Un error de validación responde "
             "`400 VALIDATION_ERROR` con un mapa `fields` (campo → mensaje).\n")
    nombres = {"identity": "Identidad y acceso", "provider": "Proveedores y negocio", "service": "Servicios y catálogo",
               "resource": "Recursos y horarios", "reservation": "Reservas",
               "common": "Errores (común a todos los módulos)"}
    total = 0
    for modulo in ("identity", "provider", "service", "resource", "reservation", "common"):
        if modulo not in cat:
            continue
        L.append("### %s (módulo `%s`)\n" % (nombres[modulo], modulo))
        for nombre, archivo, campos in cat[modulo]:
            total += 1
            L.append("**`%s`** — `%s`\n" % (nombre, archivo))
            L.append("| Campo | Tipo | Restricciones | Mensaje de error |")
            L.append("|---|---|---|---|")
            for c, tipo, reglas, msgs in campos:
                L.append("| `%s` | `%s` | %s | %s |" % (c, tipo.replace("|", "\\|"), "; ".join(reglas) or "—", " · ".join(dict.fromkeys(msgs)) or "—"))
            L.append("")
    L.append("_Total: %d DTO._\n" % total)
    with open(SALIDA, "w", encoding="utf-8") as f:
        f.write("\n".join(L))
    print("Referencia generada: %d endpoints, %d DTO -> %s" % (len(eps), total, os.path.relpath(SALIDA, RAIZ)))


if __name__ == "__main__":
    escribir()
