# -*- coding: utf-8 -*-
"""
Genera la expresión regular de `server.tomcat.remoteip.internal-proxies` (application.yml): los proxies cuyo
X-Forwarded-For se acepta. Incluye los rangos privados que Tomcat ya confía por defecto y los de Cloudflare, porque
Render recibe el tráfico a través de Cloudflare: sin ellos la aplicación guarda la IP del borde de Cloudflare
(p. ej. 104.23.x.x) en lugar de la IP del cliente (OWASP-03).

    python docs/seguridad/generar_proxies_confiables.py            # imprime la regex
    python docs/seguridad/generar_proxies_confiables.py --descargar # usa las listas vigentes de Cloudflare

Cloudflare publica sus rangos en https://www.cloudflare.com/ips-v4 y /ips-v6 (cambian muy rara vez). Si cambian,
se vuelve a ejecutar este script y se pega el resultado en application.yml.
"""
import ipaddress
import sys
import urllib.request

# Listas de Cloudflare verificadas el 2026-10-12.
IPV4 = """173.245.48.0/20 103.21.244.0/22 103.22.200.0/22 103.31.4.0/22 141.101.64.0/18 108.162.192.0/18
190.93.240.0/20 188.114.96.0/20 197.234.240.0/22 198.41.128.0/17 162.158.0.0/15 104.16.0.0/13 104.24.0.0/14
172.64.0.0/13 131.0.72.0/22""".split()
IPV6 = "2400:cb00::/32 2606:4700::/32 2803:f800::/32 2405:b500::/32 2405:8100::/32 2a06:98c0::/29 2c0f:f248::/32".split()

# Valor por defecto de Tomcat 11 (rangos privados, loopback y CGNAT); al fijar la propiedad se reemplaza, así que se repite.
TOMCAT_DEFAULT = (r"10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|169\.254\.\d{1,3}\.\d{1,3}|"
                  r"127\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.1[6-9]{1}\.\d{1,3}\.\d{1,3}|172\.2[0-9]{1}\.\d{1,3}\.\d{1,3}|"
                  r"172\.3[0-1]{1}\.\d{1,3}\.\d{1,3}|0:0:0:0:0:0:0:1|::1|100\.6[4-9]\.\d{1,3}\.\d{1,3}|"
                  r"100\.[7-9][0-9]\.\d{1,3}\.\d{1,3}|100\.1[0-1][0-9]\.\d{1,3}\.\d{1,3}|100\.12[0-7]\.\d{1,3}\.\d{1,3}")


def octet_range(lo, hi):
    """Regex de un entero entre lo y hi (0-255) sin ceros a la izquierda."""
    if lo == 0 and hi == 255:
        return r"\d{1,3}"
    groups = {}
    for v in range(lo, hi + 1):
        s = str(v)
        groups.setdefault(s[:-1], []).append(s[-1])
    alts = []
    for prefix, units in groups.items():
        units.sort()
        runs, start, prev = [], units[0], units[0]
        for u in units[1:] + [None]:
            if u is not None and int(u) == int(prev) + 1:
                prev = u
                continue
            runs.append(start if start == prev else "%s-%s" % (start, prev))
            start = prev = u
        alts.append(prefix + "[" + "".join(runs) + "]")
    return "(?:" + "|".join(alts) + ")" if len(alts) > 1 else alts[0]


def cidr4(cidr):
    net = ipaddress.ip_network(cidr)
    octets = str(net.network_address).split(".")
    full, rem = divmod(net.prefixlen, 8)
    parts = [o for o in octets[:full]]
    out = list(parts)
    if full < 4:
        block = 256 >> rem if rem else 256
        lo = int(octets[full])
        out.append(octet_range(lo, lo + block - 1))
        out += [r"\d{1,3}"] * (3 - full)
    return r"\.".join(out)


def cidr6(cidr):
    net = ipaddress.ip_network(cidr)
    groups = net.network_address.exploded.split(":")
    full, rem = divmod(net.prefixlen, 16)
    fixed = [g.lstrip("0") or "0" for g in groups[:full]]
    if rem:  # /29 -> los 3 bits finales del grupo siguiente varían
        g = int(groups[full], 16)
        top = format(g, "04x")
        lo, hi = g, g + (1 << (16 - rem)) - 1
        last = sorted({format(v, "04x")[3] for v in range(lo, hi + 1)})
        fixed.append(top[:3] + "[" + "".join(last) + "]")
    return ":".join(fixed) + ":[0-9a-f:]*"


def main():
    v4, v6 = IPV4, IPV6
    if "--descargar" in sys.argv:
        v4 = urllib.request.urlopen("https://www.cloudflare.com/ips-v4").read().decode().split()
        v6 = urllib.request.urlopen("https://www.cloudflare.com/ips-v6").read().decode().split()
    regex = "|".join([TOMCAT_DEFAULT] + [cidr4(c) for c in v4] + [cidr6(c) for c in v6])
    print(regex)


if __name__ == "__main__":
    main()
