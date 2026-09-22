# Guía para probar el backend — Proyecto Reservas (Sprint 1)

Backend desplegado, con base de datos PostgreSQL real. Cubre las 6 historias de usuario de Identidad y Acceso: registro de cliente, inicio de sesión, registro de proveedor, cierre de sesión, gestión de roles, y control de acceso por rol/pertenencia.

**URL base:** `https://proyecto2026-2-5zoo.onrender.com`

> **Nota sobre el primer request:** el servicio está en un plan gratuito que se duerme tras 15 minutos sin tráfico. Si la primera petición tarda hasta 1-2 minutos en responder (o da timeout), es normal — el servicio está despertando. Probar primero con el endpoint de salud (paso 0) resuelve esto antes de continuar.

Todos los ejemplos usan `curl` desde una terminal; también se puede usar Postman u otro cliente HTTP pegando la misma URL, método y cuerpo JSON.

## 0. Verificar que el servicio está activo

```bash
curl https://proyecto2026-2-5zoo.onrender.com/actuator/health
```

Respuesta esperada: `{"status":"UP"}` (si tarda, es el "despertar" descrito arriba; reintentar).

## 1. Registro de cliente (HU-01)

```bash
curl -X POST https://proyecto2026-2-5zoo.onrender.com/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Usuario Prueba","email":"profesor.prueba@example.com","cellphone":"3009998888","password":"Segura#2026"}'
```

Respuesta esperada: `201 Created`, con `role: "CLIENTE"` (el rol siempre lo asigna el servidor, nunca el cliente).

**Casos de rechazo para probar** (todos deben dar error, no crear cuenta):
- Repetir la misma petición → `409` (correo/celular duplicado).
- Cambiar la contraseña a algo simple como `"abc"` → `400`, con el detalle del campo en `fields`.
- Quitar el campo `cellphone` → `400`.

## 2. Inicio de sesión (HU-02)

```bash
curl -X POST https://proyecto2026-2-5zoo.onrender.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"profesor.prueba@example.com","password":"Segura#2026"}'
```

Respuesta esperada: `200`, con un `token` (JWT), `expiresIn` (3600 segundos = 1 hora) y `role`. Guarda ese token para el siguiente paso.

Con credenciales incorrectas → `401`.

## 3. Acceso con el token (HU-06)

Sustituye `<TOKEN>` por el valor de `token` del paso anterior, y `<USER_ID>` por el `id` que devolvió el registro del paso 1.

```bash
curl https://proyecto2026-2-5zoo.onrender.com/api/v1/users/<USER_ID> \
  -H "Authorization: Bearer <TOKEN>"
```

- Con el token del propio usuario sobre su propio id → `200`.
- Sin el header `Authorization` → `401`.
- Con el token de este cliente sobre el id de **otro** usuario → `403` (un cliente solo puede ver sus propios datos).

## 4. Registro de proveedor (HU-03)

```bash
curl -X POST https://proyecto2026-2-5zoo.onrender.com/api/v1/providers \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Proveedor Prueba","email":"proveedor.prueba@example.com","cellphone":"3007776666","password":"Segura#2026","businessName":"Negocio de Prueba"}'
```

Respuesta esperada: `201`, con `role: "PROVEEDOR"`, `providerId` y `businessId`.

## 5. Cierre de sesión (HU-04)

```bash
curl -X POST https://proyecto2026-2-5zoo.onrender.com/api/v1/auth/logout \
  -H "Authorization: Bearer <TOKEN>"
```

Después de esto, repetir el paso 3 con el **mismo** token debe dar `401` — el token deja de servir aunque no haya expirado, porque la sesión quedó revocada en el servidor.

## 6. Gestión de roles (HU-05, requiere una cuenta de Administrador)

Estos pasos necesitan iniciar sesión como Administrador (repetir el paso 2 con estas credenciales):

```
email: admin@example.com
password: Segura#2026
```

Con el token de administrador (`<ADMIN_TOKEN>`):

**Cambiar el rol de un usuario:**
```bash
curl -X PATCH https://proyecto2026-2-5zoo.onrender.com/api/v1/users/<USER_ID>/role \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"role":"PROVEEDOR"}'
```

**Casos de rechazo para probar:**
- El administrador intenta cambiar su propio rol → `403`.
- Intentar cambiar el rol de un usuario que ya es Proveedor → `403`.
- Pedir un rol que no existe (`{"role":"SUPERADMIN"}`) → `400`.
- Repetir cualquiera de estas peticiones con el token de un Cliente en vez del de Administrador → `403`.

**Eliminar un usuario:**
```bash
curl -X DELETE https://proyecto2026-2-5zoo.onrender.com/api/v1/users/<USER_ID> \
  -H "Authorization: Bearer <ADMIN_TOKEN>"
```
Respuesta esperada: `204`. Una consulta posterior a ese id da `404`.

## Resumen de rutas

| Método | Ruta | Requiere token | Rol |
|---|---|---|---|
| POST | `/api/v1/users` | No | — (público) |
| POST | `/api/v1/providers` | No | — (público) |
| POST | `/api/v1/auth/login` | No | — (público) |
| POST | `/api/v1/auth/logout` | Sí | Cualquiera |
| GET | `/api/v1/users/{id}` | Sí | Propio, o Administrador |
| GET | `/api/v1/providers/me` | Sí | Proveedor (el suyo) |
| GET | `/api/v1/providers/{id}` | Sí | Propio, o Administrador |
| PATCH | `/api/v1/users/{id}/role` | Sí | Administrador |
| DELETE | `/api/v1/users/{id}` | Sí | Administrador |
| GET | `/actuator/health` | No | — (público) |

## Qué no está implementado en este sprint

- Servicios, recursos, reservas y reportes (previstos para sprints posteriores).
- Cambio de la propia contraseña.
- MFA para administradores está implementado (`/api/v1/auth/mfa/setup` y `/activate`), pero no es necesario para el flujo de prueba de este documento.
