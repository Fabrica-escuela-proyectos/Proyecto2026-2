# -*- coding: utf-8 -*-
"""
Genera la colección de Postman del Sprint 2 (flujo completo contra la API) y sus entornos.

    python docs/postman/generar_coleccion.py

Escribe en esta misma carpeta:
  - Reservas-Sprint2.postman_collection.json
  - Reservas-Render.postman_environment.json
  - Reservas-Local.postman_environment.json

Se genera con un script (en vez de editar el JSON a mano) para que sea fácil de revisar y de
mantener cuando cambie un endpoint. No contiene credenciales: los usuarios de prueba se crean en
cada corrida con correos y contraseñas aleatorios; el administrador (opcional) se configura en el
entorno local de cada persona.
"""
import json
import os

AQUI = os.path.dirname(os.path.abspath(__file__))
RENDER_URL = "https://proyecto2026-2-5zoo.onrender.com"

# --------------------------------------------------------------------------- scripts reutilizables

INIT_SCRIPT = r"""
// Datos nuevos en cada corrida: nada de esto es una credencial real.
const rnd = () => Math.random().toString(36).slice(2, 10);
const run = rnd();
const phone = () => '3' + String(Math.floor(Math.random() * 1e9)).padStart(9, '0');
const set = (k, v) => pm.collectionVariables.set(k, v);

set('runId', run);
set('password', 'Qa#' + run + 'Xz');           // cumple la política: mayúscula, minúscula y carácter especial
set('providerEmail', 'qa.prov.' + run + '@example.com');
set('providerBEmail', 'qa.provb.' + run + '@example.com');
set('clientEmail', 'qa.cli.' + run + '@example.com');
set('providerPhone', phone());
set('providerBPhone', phone());
set('clientPhone', phone());

// Primer lunes que cae al menos 7 días adelante, en hora de Bogotá (UTC-5, sin horario de verano):
// el horario de prueba es lunes-viernes 08:00-20:00, así que siempre hay cupo y se cumple cualquier antelación.
const bogota = new Date(Date.now() - 5 * 3600 * 1000);
const d = new Date(Date.UTC(bogota.getUTCFullYear(), bogota.getUTCMonth(), bogota.getUTCDate() + 7));
while (d.getUTCDay() !== 1) { d.setUTCDate(d.getUTCDate() + 1); }
set('bookingDate', d.toISOString().slice(0, 10));
const y = new Date(Date.UTC(bogota.getUTCFullYear(), bogota.getUTCMonth(), bogota.getUTCDate() - 1));
set('pastDate', y.toISOString().slice(0, 10));

// Limpia lo que dejó la corrida anterior.
['providerToken', 'providerBToken', 'clientToken', 'adminToken', 'providerUserId', 'providerBUserId', 'clientUserId',
 'businessId', 'businessBId', 'serviceId', 'resourceId', 'bookingId', 'bookingId2', 'bookingId3', 'bookingId4']
    .forEach(k => pm.collectionVariables.unset(k));
"""

# TOTP (RFC 6238: HMAC-SHA1, 6 dígitos, 30 s) con la librería CryptoJS que ya trae Postman.
TOTP_SCRIPT = r"""
const cjs = require("crypto-js");
function base32ToHex(b32) {
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
    let bits = '';
    for (const c of b32.replace(/[=\s]+/g, '').toUpperCase()) { bits += alphabet.indexOf(c).toString(2).padStart(5, '0'); }
    let hex = '';
    for (let i = 0; i + 4 <= bits.length; i += 4) { hex += parseInt(bits.substr(i, 4), 2).toString(16); }
    return hex;
}
function totp(secret) {
    const key = cjs.enc.Hex.parse(base32ToHex(secret));
    const counter = Math.floor(Date.now() / 30000);
    const msg = cjs.enc.Hex.parse(counter.toString(16).padStart(16, '0'));
    const h = cjs.HmacSHA1(msg, key).toString();
    const off = parseInt(h.slice(-1), 16);
    const code = (parseInt(h.substr(off * 2, 8), 16) & 0x7fffffff) % 1000000;
    return String(code).padStart(6, '0');
}
"""

SKIP_WITHOUT_ADMIN = r"""
// Las pruebas de administrador son opcionales: se omiten si el entorno no tiene el administrador configurado.
if (!pm.environment.get('adminEmail') || !pm.environment.get('adminTotpSecret')) {
    pm.execution.skipRequest();
}
"""


def tests_status(code, name=None):
    return ["pm.test('%s', () => pm.response.to.have.status(%d));" % (name or ('Responde %d' % code), code)]


def build_url(path):
    raw = "{{baseUrl}}" + path
    pure, _, query = path.partition("?")
    segs = [s for s in pure.split("/") if s]
    url = {"raw": raw, "host": ["{{baseUrl}}"], "path": segs}
    if query:
        url["query"] = [{"key": kv.split("=")[0], "value": kv.split("=", 1)[1] if "=" in kv else ""} for kv in query.split("&")]
    return url


def item(name, method, path, auth=None, body=None, pre=None, tests=None, desc=None, headers=None):
    hdrs = []
    if body is not None:
        hdrs.append({"key": "Content-Type", "value": "application/json"})
    if auth:
        hdrs.append({"key": "Authorization", "value": "Bearer {{%s}}" % auth})
    for k, v in (headers or {}).items():
        hdrs.append({"key": k, "value": v})
    req = {"method": method, "header": hdrs, "url": build_url(path)}
    if body is not None:
        req["body"] = {"mode": "raw", "raw": body if isinstance(body, str) else json.dumps(body, ensure_ascii=False, indent=2),
                       "options": {"raw": {"language": "json"}}}
    if desc:
        req["description"] = desc
    events = []
    if pre:
        events.append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": pre.strip("\n").split("\n")}})
    if tests:
        lines = tests if isinstance(tests, list) else tests.strip("\n").split("\n")
        events.append({"listen": "test", "script": {"type": "text/javascript", "exec": lines}})
    out = {"name": name, "request": req}
    if events:
        out["event"] = events
    return out


def folder(name, items, desc=None):
    f = {"name": name, "item": items}
    if desc:
        f["description"] = desc
    return f


def save(*pairs):
    """pairs = (variable, expresión JS sobre 'j' = cuerpo JSON de la respuesta)."""
    lines = ["const j = pm.response.json();"]
    for var, expr in pairs:
        lines.append("pm.collectionVariables.set('%s', %s);" % (var, expr))
    return lines


def J(**kw):
    return json.dumps(kw, ensure_ascii=False, indent=2)


def bearer(var):
    return var


# --------------------------------------------------------------------------- contenido de la colección

def construir():
    P, B, C, A = "providerToken", "providerBToken", "clientToken", "adminToken"
    carpetas = []

    # 0 ---------------------------------------------------------------- preparación
    carpetas.append(folder("00 · Preparación", [
        item("00 Salud del servicio (inicializa la corrida)", "GET", "/actuator/health", pre=INIT_SCRIPT,
             tests=["pm.test('El servicio está UP', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().status).to.eql('UP'); });",
                    "console.log('Corrida ' + pm.collectionVariables.get('runId') + ' · fecha de reservas ' + pm.collectionVariables.get('bookingDate'));"],
             desc="Genera usuarios, contraseña y fechas nuevos para esta corrida. En Render gratis la primera petición puede tardar ~2 minutos (el servicio se duerme a los 15 min): sube el timeout de Postman a 150000 ms.")]))

    # 1 ---------------------------------------------------------------- registro y acceso
    carpetas.append(folder("01 · Registro y acceso (HU-01, 02, 03, 06)", [
        item("01 Registrar proveedor (HU-03)", "POST", "/api/v1/providers",
             body=J(fullName="Proveedor QA", email="{{providerEmail}}", cellphone="{{providerPhone}}", password="{{password}}", businessName="Negocio QA {{runId}}"),
             tests=tests_status(201) + ["pm.test('Rol PROVEEDOR y negocio creado', () => { const j = pm.response.json(); pm.expect(j.role).to.eql('PROVEEDOR'); pm.expect(j.businessId).to.be.a('string'); });"]
                   + save(("providerUserId", "j.userId"), ("businessId", "j.businessId"))),
        item("02 Registrar proveedor B (para probar accesos ajenos)", "POST", "/api/v1/providers",
             body=J(fullName="Proveedor B QA", email="{{providerBEmail}}", cellphone="{{providerBPhone}}", password="{{password}}", businessName="Negocio B QA {{runId}}"),
             tests=tests_status(201) + save(("providerBUserId", "j.userId"), ("businessBId", "j.businessId"))),
        item("03 Registrar cliente (HU-01)", "POST", "/api/v1/users",
             body=J(fullName="Cliente QA", email="{{clientEmail}}", cellphone="{{clientPhone}}", password="{{password}}"),
             tests=tests_status(201) + ["pm.test('Rol CLIENTE', () => pm.expect(pm.response.json().role).to.eql('CLIENTE'));"] + save(("clientUserId", "j.id"))),
        item("04 Registro con correo repetido → 409 (HU-01)", "POST", "/api/v1/users",
             body=J(fullName="Cliente QA", email="{{clientEmail}}", cellphone="3009999999", password="{{password}}"),
             tests=tests_status(409), desc="Cada intento de registro cuenta para el límite de 5 por IP cada 10 minutos (ver README)."),
        item("05 Login proveedor (HU-02)", "POST", "/api/v1/auth/login", body=J(email="{{providerEmail}}", password="{{password}}"),
             tests=tests_status(200) + ["pm.test('Devuelve token y rol', () => { const j = pm.response.json(); pm.expect(j.token).to.be.a('string'); pm.expect(j.role).to.eql('PROVEEDOR'); });"]
                   + save(("providerToken", "j.token"))),
        item("06 Login proveedor B", "POST", "/api/v1/auth/login", body=J(email="{{providerBEmail}}", password="{{password}}"),
             tests=tests_status(200) + save(("providerBToken", "j.token"))),
        item("07 Login cliente", "POST", "/api/v1/auth/login", body=J(email="{{clientEmail}}", password="{{password}}"),
             tests=tests_status(200) + save(("clientToken", "j.token"))),
        item("08 Login con contraseña incorrecta → 401 genérico (HU-02)", "POST", "/api/v1/auth/login",
             body=J(email="{{clientEmail}}", password="Incorrecta#1"),
             tests=tests_status(401) + ["pm.test('Mensaje genérico, sin revelar si el correo existe', () => pm.expect(pm.response.json().message).to.eql('Las credenciales no son válidas'));"]),
        item("09 Mi proveedor y mi negocio (HU-03/06)", "GET", "/api/v1/providers/me", auth=P,
             tests=tests_status(200) + ["pm.test('Incluye mi negocio', () => pm.expect(pm.response.json().businesses.map(b => b.id)).to.include(pm.collectionVariables.get('businessId')));"]),
    ]))

    # 2 ---------------------------------------------------------------- configuración del negocio
    carpetas.append(folder("02 · Configuración del negocio (HU-08, 09, 14, 18, 19)", [
        item("10 Definir antelación mínima de 1 hora (HU-08)", "PUT", "/api/v1/businesses/{{businessId}}/booking-lead-time", auth=P, body=J(hours=1),
             tests=tests_status(200) + ["pm.test('hours = 1', () => pm.expect(pm.response.json().hours).to.eql(1));"]),
        item("11 Antelación inválida (0) → 400 (HU-08)", "PUT", "/api/v1/businesses/{{businessId}}/booking-lead-time", auth=P, body=J(hours=0), tests=tests_status(400)),
        item("12 Crear servicio de 60 min (HU-09)", "POST", "/api/v1/businesses/{{businessId}}/services", auth=P,
             body=J(name="Corte de cabello", description="Incluye lavado", durationMinutes=60, priceCop=25000),
             tests=tests_status(201) + ["pm.test('Nace activo', () => pm.expect(pm.response.json().active).to.eql(true));"] + save(("serviceId", "j.id"))),
        item("13 Servicio con nombre repetido (otras mayúsculas) → 409 (HU-09)", "POST", "/api/v1/businesses/{{businessId}}/services", auth=P,
             body=J(name="CORTE DE CABELLO", durationMinutes=30, priceCop=1000), tests=tests_status(409)),
        item("14 Servicio sin campos → 400 por campo (HU-09)", "POST", "/api/v1/businesses/{{businessId}}/services", auth=P, body="{}",
             tests=tests_status(400) + ["pm.test('Errores por campo', () => { const f = pm.response.json().fields; pm.expect(f).to.have.property('name'); pm.expect(f).to.have.property('durationMinutes'); pm.expect(f).to.have.property('priceCop'); });"]),
        item("15 Registrar recurso 'Sala 1' (HU-14)", "POST", "/api/v1/businesses/{{businessId}}/resources", auth=P, body=J(name="Sala 1", type="SALA"),
             tests=tests_status(201) + save(("resourceId", "j.id"))),
        item("16 Recurso con nombre repetido → 409 (HU-14)", "POST", "/api/v1/businesses/{{businessId}}/resources", auth=P, body=J(name="sala 1", type="EQUIPO"), tests=tests_status(409)),
        item("17 Recurso con tipo inválido → 400 (HU-14)", "POST", "/api/v1/businesses/{{businessId}}/resources", auth=P, body=J(name="Mesa", type="MESA"), tests=tests_status(400)),
        item("18 Asignar el recurso al servicio (HU-18)", "PUT", "/api/v1/services/{{serviceId}}/resources", auth=P, body='{"resourceIds":["{{resourceId}}"]}',
             tests=tests_status(200) + ["pm.test('Queda asignado', () => pm.expect(pm.response.json().resources.map(r => r.name)).to.eql(['Sala 1']));"]),
        item("19 Asignar un recurso inexistente → 400 y no cambia nada (HU-18)", "PUT", "/api/v1/services/{{serviceId}}/resources", auth=P,
             body='{"resourceIds":["{{resourceId}}","00000000-0000-0000-0000-000000000000"]}', tests=tests_status(400)),
        item("20 Horario lunes a viernes 08:00-20:00 (HU-19)", "PUT", "/api/v1/resources/{{resourceId}}/availability", auth=P,
             body=json.dumps({"days": [{"dayOfWeek": d, "ranges": [{"start": "08:00", "end": "20:00"}]} for d in range(1, 6)]}, indent=2),
             tests=tests_status(200)),
        item("21 Consultar el horario: siempre 7 días (HU-19)", "GET", "/api/v1/resources/{{resourceId}}/availability", auth=P,
             tests=tests_status(200) + ["pm.test('7 días; sábado y domingo no disponibles', () => { const d = pm.response.json().days; pm.expect(d).to.have.length(7); pm.expect(d[5].ranges).to.have.length(0); pm.expect(d[6].ranges).to.have.length(0); pm.expect(d[0].ranges).to.have.length(1); });"]),
        item("22 Rango con fin anterior al inicio → 400 (HU-19)", "PUT", "/api/v1/resources/{{resourceId}}/availability/1", auth=P,
             body='{"ranges":[{"start":"14:00","end":"10:00"}]}', tests=tests_status(400)),
        item("23 Rangos superpuestos → 400 (HU-19)", "PUT", "/api/v1/resources/{{resourceId}}/availability/1", auth=P,
             body='{"ranges":[{"start":"09:00","end":"12:00"},{"start":"11:00","end":"13:00"}]}', tests=tests_status(400)),
        item("24 El horario no cambió tras los rechazos (HU-19)", "GET", "/api/v1/resources/{{resourceId}}/availability", auth=P,
             tests=["pm.test('El lunes conserva 08:00-20:00', () => { const r = pm.response.json().days[0].ranges; pm.expect(r).to.eql([{start:'08:00', end:'20:00'}]); });"]),
    ], desc="Todo lo que hace el proveedor para dejar un servicio reservable."))

    # 3 ---------------------------------------------------------------- accesos ajenos
    carpetas.append(folder("03 · Control de acceso entre proveedores (HU-06)", [
        item("25 Proveedor B crea servicio en mi negocio → 403", "POST", "/api/v1/businesses/{{businessId}}/services", auth=B,
             body=J(name="Intruso", durationMinutes=30, priceCop=1), tests=tests_status(403)),
        item("26 Proveedor B registra recurso en mi negocio → 403", "POST", "/api/v1/businesses/{{businessId}}/resources", auth=B, body=J(name="Intruso", type="SALA"), tests=tests_status(403)),
        item("27 Proveedor B cambia mi antelación → 403", "PUT", "/api/v1/businesses/{{businessId}}/booking-lead-time", auth=B, body=J(hours=5), tests=tests_status(403)),
        item("28 Proveedor B asigna recursos a mi servicio → 403", "PUT", "/api/v1/services/{{serviceId}}/resources", auth=B, body='{"resourceIds":[]}', tests=tests_status(403)),
        item("29 Proveedor B cambia el horario de mi recurso → 403", "PUT", "/api/v1/resources/{{resourceId}}/availability/1", auth=B, body='{"ranges":[]}', tests=tests_status(403)),
        item("30 Proveedor B lista las reservas de mi negocio → 403", "GET", "/api/v1/businesses/{{businessId}}/bookings", auth=B, tests=tests_status(403)),
        item("31 Cliente intenta configurar un negocio → 403", "POST", "/api/v1/businesses/{{businessId}}/services", auth=C,
             body=J(name="Intruso", durationMinutes=30, priceCop=1), tests=tests_status(403)),
        item("32 Sin sesión → 401", "POST", "/api/v1/businesses/{{businessId}}/services", body=J(name="Anónimo", durationMinutes=30, priceCop=1), tests=tests_status(401)),
    ]))

    # 4 ---------------------------------------------------------------- catálogo y disponibilidad
    carpetas.append(folder("04 · Catálogo y disponibilidad (HU-13, 20)", [
        item("33 Catálogo de negocios con sesión (HU-13)", "GET", "/api/v1/businesses?size=50", auth=C,
             tests=tests_status(200) + ["pm.test('Trae página con items', () => { const j = pm.response.json(); pm.expect(j.items).to.be.an('array'); pm.expect(j.size).to.be.at.most(50); });"]),
        item("34 Catálogo sin sesión → 401 (HU-13)", "GET", "/api/v1/businesses", tests=tests_status(401)),
        item("35 Detalle del negocio con sus servicios activos (HU-13)", "GET", "/api/v1/businesses/{{businessId}}", auth=C,
             tests=tests_status(200) + ["pm.test('Muestra el servicio con duración y precio', () => { const s = pm.response.json().services[0]; pm.expect(s.name).to.eql('Corte de cabello'); pm.expect(s.durationMinutes).to.eql(60); pm.expect(s.priceCop).to.eql(25000); });"]),
        item("36 Negocio inexistente → 404 (HU-13)", "GET", "/api/v1/businesses/00000000-0000-0000-0000-000000000000", auth=C, tests=tests_status(404)),
        item("37 Horarios libres SIN sesión: la consulta es pública (HU-20)", "GET", "/api/v1/services/{{serviceId}}/availability?date={{bookingDate}}",
             tests=tests_status(200) + ["pm.test('Hay horarios cada 60 min desde las 08:00 y el de las 10:00 está libre', () => { const s = pm.response.json().slots.map(x => x.start); pm.expect(s).to.include.members(['08:00', '10:00', '11:00', '14:00']); });"]),
        item("38 Fecha con formato inválido → 400 (HU-20)", "GET", "/api/v1/services/{{serviceId}}/availability?date=2026-99-99", tests=tests_status(400)),
        item("39 Fecha pasada → 400 (HU-20)", "GET", "/api/v1/services/{{serviceId}}/availability?date={{pastDate}}", tests=tests_status(400)),
        item("40 Servicio inexistente → 404 (HU-20)", "GET", "/api/v1/services/00000000-0000-0000-0000-000000000000/availability?date={{bookingDate}}", tests=tests_status(404)),
        item("41 Sábado: sin horario definido (HU-20)", "GET", "/api/v1/services/{{serviceId}}/availability?date={{bookingDate}}",
             pre="""const d = new Date(pm.collectionVariables.get('bookingDate') + 'T00:00:00Z'); d.setUTCDate(d.getUTCDate() + 5);
pm.request.url.query.remove('date'); pm.request.url.query.add({key: 'date', value: d.toISOString().slice(0, 10)});""",
             tests=tests_status(200) + ["pm.test('Sin horarios y con mensaje', () => { const j = pm.response.json(); pm.expect(j.slots).to.have.length(0); pm.expect(j.message).to.be.a('string'); });"]),
    ]))

    # 5 ---------------------------------------------------------------- reservas
    def reserva(name, start, end, var=None, extra_tests=None, auth=C, body=None, code=201):
        b = body or J(serviceId="{{serviceId}}", date="{{bookingDate}}", startTime=start, endTime=end)
        t = tests_status(code)
        if code == 201:
            t += ["pm.test('Reserva CONFIRMADA con id y precio vigente', () => { const j = pm.response.json(); pm.expect(j.status).to.eql('CONFIRMADA'); pm.expect(j.id).to.be.a('string'); pm.expect(j.priceCop).to.eql(25000); });"]
            if var:
                t += save((var, "j.id"))
        return item(name, "POST", "/api/v1/bookings", auth=auth, body=b, tests=t + (extra_tests or []))

    carpetas.append(folder("05 · Reservas (HU-22, 23, 24)", [
        reserva("42 Crear reserva 10:00-11:00 (HU-22)", "10:00", "11:00", var="bookingId"),
        item("43 Mismo horario otra vez → 409 (HU-22)", "POST", "/api/v1/bookings", auth=C,
             body=J(serviceId="{{serviceId}}", date="{{bookingDate}}", startTime="10:00", endTime="11:00"),
             tests=tests_status(409) + ["pm.test('Informa que el horario no está disponible', () => pm.expect(pm.response.json().message).to.include('no está disponible'));"]),
        item("44 Traslape parcial 10:30-11:30 → 409 (HU-22)", "POST", "/api/v1/bookings", auth=C,
             body=J(serviceId="{{serviceId}}", date="{{bookingDate}}", startTime="10:30", endTime="11:30"), tests=tests_status(409)),
        item("45 Fuera del horario del recurso (06:00-07:00) → 409 (HU-22/19)", "POST", "/api/v1/bookings", auth=C,
             body=J(serviceId="{{serviceId}}", date="{{bookingDate}}", startTime="06:00", endTime="07:00"), tests=tests_status(409)),
        item("46 Sin fecha → 400 por campo (HU-22)", "POST", "/api/v1/bookings", auth=C,
             body=J(serviceId="{{serviceId}}", startTime="12:00", endTime="13:00"),
             tests=tests_status(400) + ["pm.test('Pide la fecha', () => pm.expect(pm.response.json().fields).to.have.property('date'));"]),
        item("47 Hora de fin anterior al inicio → 400 (HU-22)", "POST", "/api/v1/bookings", auth=C,
             body=J(serviceId="{{serviceId}}", date="{{bookingDate}}", startTime="13:00", endTime="12:00"), tests=tests_status(400)),
        item("48 Duración distinta a la del servicio → 400 (HU-22)", "POST", "/api/v1/bookings", auth=C,
             body=J(serviceId="{{serviceId}}", date="{{bookingDate}}", startTime="12:00", endTime="13:30"), tests=tests_status(400)),
        item("49 Un proveedor no puede reservar → 403 (HU-22)", "POST", "/api/v1/bookings", auth=P,
             body=J(serviceId="{{serviceId}}", date="{{bookingDate}}", startTime="12:00", endTime="13:00"), tests=tests_status(403)),
        item("50 Reservar sin sesión → 401 (HU-22)", "POST", "/api/v1/bookings",
             body=J(serviceId="{{serviceId}}", date="{{bookingDate}}", startTime="12:00", endTime="13:00"), tests=tests_status(401)),
        item("51 El horario reservado ya no aparece libre (HU-20 + 22)", "GET", "/api/v1/services/{{serviceId}}/availability?date={{bookingDate}}",
             tests=tests_status(200) + ["pm.test('10:00 ocupado y 11:00 libre', () => { const s = pm.response.json().slots.map(x => x.start); pm.expect(s).to.not.include('10:00'); pm.expect(s).to.include('11:00'); });"]),
        item("52 Mis reservas (HU-23)", "GET", "/api/v1/bookings/me", auth=C,
             tests=tests_status(200) + ["pm.test('Contiene mi reserva con fecha, horas, servicio y estado', () => { const it = pm.response.json().items.find(i => i.id === pm.collectionVariables.get('bookingId')); pm.expect(it).to.exist; pm.expect(it.date).to.eql(pm.collectionVariables.get('bookingDate')); pm.expect(it.startTime).to.eql('10:00'); pm.expect(it.endTime).to.eql('11:00'); pm.expect(it.serviceName).to.eql('Corte de cabello'); pm.expect(it.status).to.eql('CONFIRMADA'); });"]),
        item("53 Ver las reservas de otro usuario → 403 (HU-23)", "GET", "/api/v1/users/{{providerUserId}}/bookings", auth=C, tests=tests_status(403)),
        item("54 Mis reservas sin sesión → 401 (HU-23)", "GET", "/api/v1/bookings/me", tests=tests_status(401)),
        item("55 El proveedor ve las reservas de su negocio con los datos del cliente (HU-24)", "GET", "/api/v1/businesses/{{businessId}}/bookings", auth=P,
             tests=tests_status(200) + ["pm.test('Incluye la reserva con nombre, correo e id del cliente', () => { const it = pm.response.json().items.find(i => i.id === pm.collectionVariables.get('bookingId')); pm.expect(it).to.exist; pm.expect(it.clientName).to.eql('Cliente QA'); pm.expect(it.clientEmail).to.eql(pm.collectionVariables.get('clientEmail')); pm.expect(it.clientId).to.eql(pm.collectionVariables.get('clientUserId')); });"]),
        item("56 Un cliente no puede ver las reservas del negocio → 403 (HU-24)", "GET", "/api/v1/businesses/{{businessId}}/bookings", auth=C, tests=tests_status(403)),
    ]))

    # 6 ---------------------------------------------------------------- cancelaciones
    carpetas.append(folder("06 · Cancelaciones (HU-25, 26)", [
        item("57 El cliente cancela su reserva (HU-25)", "POST", "/api/v1/bookings/{{bookingId}}/cancellation", auth=C, body=J(reason="No puedo asistir"),
             tests=tests_status(200) + ["pm.test('CANCELADA, origen CLIENTE y motivo', () => { const j = pm.response.json(); pm.expect(j.status).to.eql('CANCELADA'); pm.expect(j.cancelOrigin).to.eql('CLIENTE'); pm.expect(j.cancelReason).to.eql('No puedo asistir'); });"]),
        item("58 Cancelar de nuevo → 409 (HU-25)", "POST", "/api/v1/bookings/{{bookingId}}/cancellation", auth=C, tests=tests_status(409)),
        item("59 El horario vuelve a estar libre (HU-25 + 20)", "GET", "/api/v1/services/{{serviceId}}/availability?date={{bookingDate}}",
             tests=["pm.test('10:00 libre otra vez', () => pm.expect(pm.response.json().slots.map(x => x.start)).to.include('10:00'));"]),
        reserva("60 Nueva reserva 11:00-12:00 (para cancelar como proveedor)", "11:00", "12:00", var="bookingId2"),
        item("61 Proveedor cancela SIN motivo → 400 (HU-26)", "POST", "/api/v1/bookings/{{bookingId2}}/provider-cancellation", auth=P, body="{}", tests=tests_status(400)),
        item("62 Proveedor B cancela una reserva ajena → 403 (HU-26)", "POST", "/api/v1/bookings/{{bookingId2}}/provider-cancellation", auth=B, body=J(reason="Intruso"), tests=tests_status(403)),
        item("63 El cliente no puede usar la cancelación del proveedor → 403 (HU-26)", "POST", "/api/v1/bookings/{{bookingId2}}/provider-cancellation", auth=C, body=J(reason="Yo"), tests=tests_status(403)),
        item("64 El proveedor cancela con motivo (HU-26)", "POST", "/api/v1/bookings/{{bookingId2}}/provider-cancellation", auth=P, body=J(reason="El recurso está en mantenimiento"),
             tests=tests_status(200) + ["pm.test('CANCELADA con origen PROVEEDOR', () => { const j = pm.response.json(); pm.expect(j.status).to.eql('CANCELADA'); pm.expect(j.cancelOrigin).to.eql('PROVEEDOR'); });"]),
        item("65 El cliente ve el motivo en sus reservas (HU-26 + 23)", "GET", "/api/v1/bookings/me?status=CANCELADA", auth=C,
             tests=tests_status(200) + ["pm.test('Ve el motivo del proveedor', () => { const it = pm.response.json().items.find(i => i.id === pm.collectionVariables.get('bookingId2')); pm.expect(it.cancelReason).to.eql('El recurso está en mantenimiento'); pm.expect(it.cancelOrigin).to.eql('PROVEEDOR'); });"]),
    ]))

    # 7 ---------------------------------------------------------------- desactivar / reactivar recurso
    carpetas.append(folder("07 · Desactivar y reactivar recurso (HU-16, 17)", [
        reserva("66 Reserva futura 14:00-15:00 en el recurso", "14:00", "15:00", var="bookingId3"),
        item("67 Desactivar sin confirmar → 409 CONFIRMATION_REQUIRED con la cantidad (HU-16)", "POST", "/api/v1/resources/{{resourceId}}/deactivation", auth=P, body=J(confirm=False),
             tests=tests_status(409) + ["pm.test('Pide confirmación e informa 1 reserva afectada', () => { const j = pm.response.json(); pm.expect(j.error).to.eql('CONFIRMATION_REQUIRED'); pm.expect(j.fields.affectedBookings).to.eql('1'); });"]),
        item("68 El recurso sigue activo y ofreciendo horarios (HU-16)", "GET", "/api/v1/services/{{serviceId}}/availability?date={{bookingDate}}",
             tests=["pm.test('Sigue habiendo horarios', () => pm.expect(pm.response.json().slots.length).to.be.above(0));"]),
        item("69 Proveedor B intenta desactivar mi recurso → 403 (HU-16)", "POST", "/api/v1/resources/{{resourceId}}/deactivation", auth=B, body=J(confirm=True), tests=tests_status(403)),
        item("70 Desactivar confirmando: cancela las reservas futuras (HU-16)", "POST", "/api/v1/resources/{{resourceId}}/deactivation", auth=P, body=J(confirm=True),
             tests=tests_status(200) + ["pm.test('Inactivo y 1 reserva cancelada', () => { const j = pm.response.json(); pm.expect(j.active).to.eql(false); pm.expect(j.cancelledBookings).to.eql(1); });"]),
        item("71 La reserva quedó cancelada por RECURSO_NO_DISPONIBLE (HU-16)", "GET", "/api/v1/bookings/me?status=CANCELADA", auth=C,
             tests=["pm.test('Origen RECURSO_NO_DISPONIBLE', () => { const it = pm.response.json().items.find(i => i.id === pm.collectionVariables.get('bookingId3')); pm.expect(it.cancelOrigin).to.eql('RECURSO_NO_DISPONIBLE'); });"]),
        item("72 Sin el recurso ya no hay horarios (HU-16 + 20)", "GET", "/api/v1/services/{{serviceId}}/availability?date={{bookingDate}}",
             tests=["pm.test('Sin horarios', () => pm.expect(pm.response.json().slots).to.have.length(0));"]),
        item("73 No se puede reservar en un recurso inactivo → 409 (HU-16 + 22)", "POST", "/api/v1/bookings", auth=C,
             body=J(serviceId="{{serviceId}}", date="{{bookingDate}}", startTime="16:00", endTime="17:00"), tests=tests_status(409)),
        item("74 Reactivar el mismo recurso (HU-17)", "POST", "/api/v1/resources/{{resourceId}}/reactivation", auth=P,
             tests=tests_status(200) + ["pm.test('Activo y es el mismo recurso', () => { const j = pm.response.json(); pm.expect(j.active).to.eql(true); pm.expect(j.resourceId).to.eql(pm.collectionVariables.get('resourceId')); });"]),
        item("75 Reactivar otra vez es idempotente (HU-17)", "POST", "/api/v1/resources/{{resourceId}}/reactivation", auth=P, tests=tests_status(200)),
        item("76 Vuelve a ofrecer horarios y las reservas canceladas siguen canceladas (HU-17)", "GET", "/api/v1/services/{{serviceId}}/availability?date={{bookingDate}}",
             tests=["pm.test('Hay horarios otra vez', () => pm.expect(pm.response.json().slots.length).to.be.above(0));"]),
        item("77 Proveedor B no puede reactivar mi recurso → 403 (HU-17)", "POST", "/api/v1/resources/{{resourceId}}/reactivation", auth=B, tests=tests_status(403)),
    ]))

    # 8 ---------------------------------------------------------------- administrador (opcional)
    admin_login_pre = SKIP_WITHOUT_ADMIN + TOTP_SCRIPT + "\npm.collectionVariables.set('adminCode', totp(pm.environment.get('adminTotpSecret')));"
    admin_delete_pre = SKIP_WITHOUT_ADMIN + TOTP_SCRIPT + "\npm.collectionVariables.set('adminCode', totp(pm.environment.get('adminTotpSecret')));"
    carpetas.append(folder("08 · Administrador y eliminación de cuentas (HU-05, 28) — OPCIONAL", [
        reserva("78 Reserva futura 16:00-17:00 que se cancelará al eliminar la cuenta", "16:00", "17:00", var="bookingId4"),
        item("79 Login de administrador SIN código → 401 MFA_REQUIRED (HU-02/ADR-004)", "POST", "/api/v1/auth/login",
             pre=SKIP_WITHOUT_ADMIN, body=J(email="{{adminEmail}}", password="{{adminPassword}}"),
             tests=tests_status(401) + ["pm.test('Pide el segundo factor', () => pm.expect(pm.response.json().error).to.eql('MFA_REQUIRED'));"],
             desc="Requiere un administrador con MFA ya activada y las variables adminEmail, adminPassword y adminTotpSecret en el entorno."),
        item("80 Login de administrador con código TOTP", "POST", "/api/v1/auth/login", pre=admin_login_pre,
             body=J(email="{{adminEmail}}", password="{{adminPassword}}", mfaCode="{{adminCode}}"),
             tests=tests_status(200) + save(("adminToken", "j.token"))),
        item("81 Eliminar al cliente SIN código MFA → 401 (HU-05)", "DELETE", "/api/v1/users/{{clientUserId}}", auth=A, pre=SKIP_WITHOUT_ADMIN, tests=tests_status(401)),
        item("82 Eliminar al cliente con código MFA (HU-05/28)", "DELETE", "/api/v1/users/{{clientUserId}}", auth=A, pre=admin_delete_pre,
             headers={"X-MFA-Code": "{{adminCode}}"}, tests=tests_status(204)),
        item("83 Su reserva futura quedó CANCELADA por ELIMINACION_CUENTA y se conserva como historial (HU-28)", "GET", "/api/v1/businesses/{{businessId}}/bookings?status=CANCELADA", auth=P,
             pre=SKIP_WITHOUT_ADMIN,
             tests=tests_status(200) + ["pm.test('Cancelada por eliminación de cuenta, con los datos del cliente conservados', () => { const it = pm.response.json().items.find(i => i.id === pm.collectionVariables.get('bookingId4')); pm.expect(it).to.exist; pm.expect(it.cancelOrigin).to.eql('ELIMINACION_CUENTA'); pm.expect(it.clientId).to.eql(null); pm.expect(it.clientName).to.eql('Cliente QA'); });"]),
        item("84 El cliente eliminado ya no puede iniciar sesión", "POST", "/api/v1/auth/login", pre=SKIP_WITHOUT_ADMIN, body=J(email="{{clientEmail}}", password="{{password}}"), tests=tests_status(401)),
        item("85 Limpieza: eliminar al proveedor B", "DELETE", "/api/v1/users/{{providerBUserId}}", auth=A, pre=admin_delete_pre, headers={"X-MFA-Code": "{{adminCode}}"}, tests=tests_status(204)),
        item("86 Limpieza: eliminar al proveedor (cancela sus reservas y borra sus servicios, HU-28)", "DELETE", "/api/v1/users/{{providerUserId}}", auth=A, pre=admin_delete_pre,
             headers={"X-MFA-Code": "{{adminCode}}"}, tests=tests_status(204)),
        item("87 Sus servicios dejaron de existir (HU-28)", "GET", "/api/v1/services/{{serviceId}}/availability?date={{bookingDate}}", pre=SKIP_WITHOUT_ADMIN, tests=tests_status(404)),
    ], desc="Se omite sola si el entorno no tiene adminEmail y adminTotpSecret. Sin ella, los usuarios de prueba (con correos qa.*@example.com) quedan en la base del servicio probado."))

    return carpetas


def coleccion():
    return {
        "info": {
            "name": "Plataforma de Reservas · Sprint 2 (flujo completo)",
            "description": ("Prueba de extremo a extremo de la API del Sprint 2: registro, configuración del negocio, catálogo, "
                            "disponibilidad pública, reservas, cancelaciones, desactivación de recursos y eliminación de cuentas. "
                            "Cada petición trae sus pruebas (pestaña Tests) y se ejecuta de principio a fin con el Collection Runner. "
                            "Lee docs/postman/README.md antes de correrla contra Render (límite de registros, tiempo de arranque)."),
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "item": construir(),
        "variable": [{"key": "baseUrl", "value": RENDER_URL}],
    }


def entorno(nombre, url):
    return {
        "name": nombre,
        "values": [
            {"key": "baseUrl", "value": url, "type": "default", "enabled": True},
            {"key": "adminEmail", "value": "", "type": "default", "enabled": True},
            {"key": "adminPassword", "value": "", "type": "secret", "enabled": True},
            {"key": "adminTotpSecret", "value": "", "type": "secret", "enabled": True},
        ],
        "_postman_variable_scope": "environment",
    }


def escribir(nombre, datos):
    with open(os.path.join(AQUI, nombre), "w", encoding="utf-8") as f:
        json.dump(datos, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    col = coleccion()
    escribir("Reservas-Sprint2.postman_collection.json", col)
    escribir("Reservas-Render.postman_environment.json", entorno("Reservas · Render", RENDER_URL))
    escribir("Reservas-Local.postman_environment.json", entorno("Reservas · Local", "http://localhost:8080"))
    n = sum(len(c["item"]) for c in col["item"])
    print("Colección generada: %d carpetas, %d peticiones" % (len(col["item"]), n))
