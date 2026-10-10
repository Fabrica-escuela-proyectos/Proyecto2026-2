# Colección de Postman — Sprint 2

Prueba de extremo a extremo de la API: registro y acceso, configuración del negocio, catálogo, disponibilidad pública, reservas, cancelaciones, desactivación de recursos y eliminación de cuentas. Son **88 peticiones con 119 aserciones**; cada una trae sus pruebas en la pestaña *Tests* y la corrida completa tarda unos 10 segundos (más el arranque de Render).

| Archivo | Para qué |
|---|---|
| [`Reservas-Sprint2.postman_collection.json`](Reservas-Sprint2.postman_collection.json) | La colección |
| [`Reservas-Render.postman_environment.json`](Reservas-Render.postman_environment.json) | Entorno apuntando al servicio desplegado |
| [`Reservas-Local.postman_environment.json`](Reservas-Local.postman_environment.json) | Entorno apuntando a `http://localhost:8080` |
| [`generar_coleccion.py`](generar_coleccion.py) | Genera los tres JSON (`python docs/postman/generar_coleccion.py`). **Edita este script, no los JSON**, cuando cambie un endpoint |

No contiene credenciales: en cada corrida crea usuarios con correos `qa.*@example.com` y una contraseña aleatoria, y fechas nuevas.

## Cómo correrla

1. En Postman: **Import** → arrastra la colección y el entorno que vayas a usar. Selecciona el entorno arriba a la derecha.
2. **Settings → Request timeout in ms = 150000.** En Render gratis el servicio se duerme a los 15 min y la primera petición tarda ~2 minutos en despertar.
3. Clic derecho en la colección → **Run collection** → *Run*. Las peticiones deben ir **en el orden** de la colección (cada una usa lo que guardó la anterior).
4. Alternativa sin interfaz (CI o terminal): `npx newman run docs/postman/Reservas-Sprint2.postman_collection.json -e docs/postman/Reservas-Render.postman_environment.json --timeout-request 150000`.

Resultado esperado: **0 fallos**. Si algo falla, el nombre de la petición lleva el número de HU para ubicarlo.

## Qué cubre cada carpeta

| Carpeta | HU | Peticiones |
|---|---|---|
| 00 · Preparación | — | Salud del servicio; genera los datos de la corrida |
| 01 · Registro y acceso | 01, 02, 03, 06 | Registrar proveedor, proveedor B y cliente; duplicado 409; logins; credenciales inválidas; mi proveedor |
| 02 · Configuración del negocio | 08, 09, 14, 18, 19 | Antelación, servicio, recurso, asignación y horario semanal, con sus errores (duplicados, rangos inválidos, atomicidad) |
| 03 · Control de acceso entre proveedores | 06 | El proveedor B, un cliente y un anónimo reciben 403/401 sobre el negocio ajeno |
| 04 · Catálogo y disponibilidad | 13, 20 | Catálogo con sesión, disponibilidad pública, fechas inválidas y pasadas, día sin horario |
| 05 · Reservas | 22, 23, 24 | Reservar, horario ocupado/traslapado/fuera de horario, validaciones, mis reservas, reservas del negocio con datos del cliente |
| 06 · Cancelaciones | 25, 26 | El cliente cancela, el horario se libera, el proveedor cancela con motivo, cada rol solo lo suyo |
| 07 · Desactivar y reactivar recurso | 16, 17 | `409 CONFIRMATION_REQUIRED`, desactivar confirmando, ya no ofrece horarios ni acepta reservas, reactivar |
| 08 · Administrador y eliminación de cuentas | 05, 28 | **Opcional.** Login con MFA, eliminar cliente y proveedores, reservas canceladas por `ELIMINACION_CUENTA`, limpieza |

## Límites y avisos

- **Límite de registros: 5 por IP cada 10 minutos** (el sexto bloquea 15 minutos). Una corrida usa **4** (proveedor, proveedor B, cliente y el duplicado de la 04). **No la corras dos veces seguidas en Render**: espera 10 minutos. En un servidor propio puedes subir `RATE_LIMIT_REGISTRATION_MAX_ATTEMPTS`.
- **Datos que deja:** sin la carpeta 08, los usuarios de prueba (`qa.*@example.com`), su negocio, servicio, recurso y reservas **quedan en la base del servicio**. La 08 los elimina. Si no tienes un administrador, avísale a quien lo tenga para que los borre, o déjalos: no molestan, pero ocupan la base gratuita de Render.
- **Carpeta 08 (administrador):** se omite sola si el entorno no tiene `adminEmail` y `adminTotpSecret`. Para usarla necesitas un administrador **con MFA ya activada** (ADR-004; el administrador del bootstrap debe enrolarla con `/api/v1/auth/mfa/setup` y `/activate`). En el entorno, **solo en tu copia local y nunca en el repositorio**, completa `adminEmail`, `adminPassword` y `adminTotpSecret` (la clave Base32 que sale en el `otpauthUri` del *setup*, la parte `secret=…`). La colección calcula el código TOTP con `require('crypto-js')` y se omite con `pm.execution.skipRequest()`, que necesita una versión reciente de Postman (≥ 10.18) o Newman.
- **Fechas:** usa el primer lunes que cae al menos 7 días adelante (hora de Bogotá). El horario de prueba es lunes a viernes de 08:00 a 20:00.
- **Orden de ejecución:** las peticiones dependen unas de otras; si ejecutas una suelta sin haber corrido las anteriores fallará por falta de variables (`businessId`, `serviceId`…), no por un error de la API.

## Cómo se validó

La colección se ejecutó con Newman contra la aplicación corriendo localmente (PostgreSQL 16 temporal, las 12 migraciones aplicadas desde cero): **0 fallos**, tanto con la carpeta de administrador (119 aserciones, TOTP incluido) como sin ella (108 aserciones), y en corridas consecutivas. **Contra Render todavía no se ha ejecutado** (es la prueba pendiente del servicio desplegado).
