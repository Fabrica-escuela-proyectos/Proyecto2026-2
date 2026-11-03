# -*- coding: utf-8 -*-
"""Markdown del repo -> PDF A4 con enlaces clicables a GitHub (PyMuPDF Story).

    python docs/entrega-final/generar_pdf.py docs/entrega-final/informe-final-proyecto.md "Informe final · Plataforma de Reservas · Arquitectura de Software"
    python docs/entrega-final/generar_pdf.py docs/entrega-final/guia-sustentacion.md "Guía de sustentación · Plataforma de Reservas · Arquitectura de Software"

Los enlaces relativos apuntan a la rama main de GitHub; los diagramas se incrustan como SVG (vectoriales).
"""
import html
import os
import re
import sys

import pymupdf

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
GH = "https://github.com/Fabrica-escuela-proyectos/Proyecto2026-2/blob/main/"
IMG_DIR = os.path.join(REPO, "docs", "arquitectura", "diagramas")

src = sys.argv[1]
footer = sys.argv[2]
out = os.path.splitext(src)[0] + ".pdf"
base_dir = os.path.relpath(os.path.dirname(os.path.abspath(src)), REPO).replace("\\", "/") + "/"
md = open(src, encoding="utf-8").read()
links = 0


def resolve(url):
    if url.startswith("http"):
        return url
    if url.startswith("#"):
        return None
    path = os.path.normpath(os.path.join(base_dir, url.split("#")[0])).replace("\\", "/")
    return GH + path


def inline(s):
    global links
    spans = []

    def keep(text):
        spans.append(text)
        return "\x00%d\x00" % (len(spans) - 1)

    # código primero: lo de adentro no se toca
    s = re.sub(r"`([^`]+)`", lambda m: keep("<code>%s</code>" % html.escape(m.group(1), quote=False)), s)

    def link(m):
        global links
        url = resolve(m.group(2))
        text = inline_simple(m.group(1))
        if not url:
            return keep(text)
        links += 1
        return keep('<a href="%s">%s</a>' % (html.escape(url), text))

    s = re.sub(r"\[([^\]]*)\]\(([^)]*)\)", link, s)
    s = inline_simple(s)
    # Los marcadores pueden anidarse (código dentro del texto de un enlace): reemplazar hasta que no quede ninguno.
    while "\x00" in s:
        s = re.sub("\x00(\\d+)\x00", lambda m: spans[int(m.group(1))], s)
    return s


def inline_simple(s):
    s = html.escape(s, quote=False)
    s = re.sub("\x00(\\d+)\x00", lambda m: m.group(0), s)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", s)
    s = re.sub(r"(?<![\w*])\*([^*\s][^*]*)\*(?![\w*])", r"<i>\1</i>", s)
    return s


out_html = []
lines = md.split("\n")
i = 0
while i < len(lines):
    ln = lines[i]
    st = ln.strip()
    if ln.startswith("# "):
        out_html.append("<h1>%s</h1>" % inline(ln[2:]))
    elif ln.startswith("## "):
        out_html.append("<h2>%s</h2>" % inline(ln[3:]))
    elif ln.startswith("### "):
        out_html.append("<h3>%s</h3>" % inline(ln[4:]))
    elif st == "---":
        out_html.append("<hr/>")
    elif st.startswith("!["):
        m = re.match(r"!\[[^\]]*\]\(([^)]+)\)", st)
        out_html.append('<p style="text-align:center"><img src="%s" width="523"/></p>' % os.path.basename(m.group(1)).replace(".png", ".svg"))
    elif st.startswith(">"):
        parts = []
        while i < len(lines) and lines[i].strip().startswith(">"):
            parts.append(lines[i].strip().lstrip(">").strip())
            i += 1
        i -= 1
        out_html.append("<blockquote>%s</blockquote>" % inline(" ".join(parts)))
    elif st.startswith("|"):
        rows = []
        while i < len(lines) and lines[i].strip().startswith("|"):
            rows.append([c.strip() for c in re.split(r"(?<!\\)\|", lines[i].strip().strip("|"))])
            i += 1
        i -= 1
        head, body = rows[0], rows[2:]
        t = ["<table><tr>%s</tr>" % "".join("<th>%s</th>" % inline(c) for c in head)]
        for r in body:
            t.append("<tr>%s</tr>" % "".join("<td>%s</td>" % inline(c.replace("\\|", "|")) for c in r))
        t.append("</table>")
        out_html.append("".join(t))
    elif re.match(r"^\s*([*-]|\d+\.)\s+", ln):
        ordered = bool(re.match(r"^\s*\d+\.", ln))
        items = []
        while i < len(lines) and re.match(r"^\s*([*-]|\d+\.)\s+", lines[i]):
            items.append(re.sub(r"^\s*([*-]|\d+\.)\s+", "", lines[i]))
            i += 1
        i -= 1
        tag = "ol" if ordered else "ul"
        out_html.append("<%s>%s</%s>" % (tag, "".join("<li>%s</li>" % inline(x) for x in items), tag))
    elif st == "":
        pass
    else:
        out_html.append("<p>%s</p>" % inline(ln))
    i += 1

CSS = """
body { font-family: sans-serif; font-size: 8.8pt; line-height: 1.28; }
h1 { font-size: 17pt; margin: 0 0 4pt 0; color: #0d1b2a; }
h2 { font-size: 12.5pt; margin: 10pt 0 3pt 0; color: #0d47a1; border-bottom: 1px solid #90a4ae; }
h3 { font-size: 10.5pt; margin: 7pt 0 2pt 0; color: #1b3a5c; }
p { margin: 2pt 0 3pt 0; }
ul, ol { margin: 2pt 0 4pt 0; padding-left: 14pt; }
li { margin: 0 0 2pt 0; }
code { font-family: monospace; font-size: 7.8pt; background-color: #eceff1; }
table { border-collapse: collapse; width: 100%; margin: 3pt 0 5pt 0; }
th { color: #0d1b2a; font-size: 8pt; text-align: left; padding: 2pt 3pt; border: 1px solid #546e7a; border-bottom: 2px solid #1b3a5c; }
td { border: 1px solid #b0bec5; padding: 2pt 3pt; vertical-align: top; font-size: 8pt; }
a { color: #0d47a1; }
blockquote { background-color: #eef4fb; border-left: 3px solid #1565c0; padding: 3pt 6pt; margin: 3pt 0; }
hr { border: 0; border-top: 1px solid #cfd8dc; }
"""

story = pymupdf.Story("<html><body>%s</body></html>" % "\n".join(out_html), user_css=CSS, archive=pymupdf.Archive(IMG_DIR))
mediabox = pymupdf.paper_rect("a4")
where = mediabox + (36, 36, -36, -40)
doc = story.write_with_links(lambda n, filled: (mediabox, where, pymupdf.Identity))
for n, page in enumerate(doc, 1):
    page.insert_text((36, mediabox.height - 22), "%s · página %d de %d" % (footer, n, len(doc)), fontsize=7,
                     color=(0.4, 0.4, 0.4), fontname="helv")
open(out, "wb").write(doc.tobytes(garbage=3, deflate=True))
check = pymupdf.open(out)
real = sum(1 for p in check for l in p.get_links() if l.get("uri"))
print("PDF:", os.path.relpath(out, REPO), "| páginas:", len(check), "| enlaces en el MD:", links, "| enlaces en el PDF:", real)
