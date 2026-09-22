# Guía de despliegue en Render

Cómo poner el backend en una URL pública en Render, con Postgres administrado. Probado localmente construyendo la imagen y levantándola contra el Postgres de `docker-compose.yml` — arrancó, migró el esquema y `/actuator/health` respondió `UP`. No se probó todavía dentro de Render mismo; sigue esta guía paso a paso y avísame en qué falla si algo no calza.

## 0. Qué se agregó para esto

- [`reservas-backend/Dockerfile`](../reservas-backend/Dockerfile): build en dos etapas (JDK 17 para compilar — obligatorio, ver la nota de Lombok en `estado-proyecto-sprint-1.md` — y JRE 17 liviano para correr). Usa `${PORT}` para el puerto, porque Render lo asigna en tiempo de ejecución, no es fijo.
- [`reservas-backend/.dockerignore`](../reservas-backend/.dockerignore).

Ambos archivos están sin commitear todavía — revísalos y commitéalos antes de conectar el repo a Render.

## 1. Crear la base de datos en Render

1. Dashboard de Render → **New → PostgreSQL**.
2. Nombre: `reservas-db` (o el que quieras), región la más cercana, plan **Free**.
3. **Importante — el plan Free expira en 90 días** y Render borra la base si no la actualizas a un plan pago antes de esa fecha. Para una demo de la materia alcanza de sobra, pero no la dejes como base "definitiva" del proyecto sin más plan.
4. Cuando esté creada, en la pestaña **Info** vas a ver por separado: `Hostname`, `Port`, `Database`, `Username`, `Password`. Los necesitas en el paso 3.

## 2. Preparar el repo

1. Confirma que `Dockerfile` y `.dockerignore` estén commiteados en `main`.
2. Nada más del código necesita cambiar — `application.yml` ya lee todo por variables de entorno.

## 3. Crear el Web Service

1. Dashboard de Render → **New → Web Service** → conecta el repo `Fabrica-escuela-proyectos/Proyecto2026-2`.
2. **Root Directory:** `reservas-backend` (el Dockerfile no está en la raíz del repo).
3. **Runtime:** Render debería detectar el `Dockerfile` solo. Si te pregunta, elige **Docker**.
4. Plan **Free** (para una demo alcanza; se "duerme" tras 15 min sin tráfico y el primer request después tarda ~30-50s en despertar — avísale a quien vea la demo, o pega el request de `/actuator/health` un par de minutos antes).
5. En **Environment Variables**, agrega:

   | Variable | Valor |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://<Hostname del paso 1>:<Port>/<Database>` |
   | `DB_USERNAME` | `<Username del paso 1>` |
   | `DB_PASSWORD` | `<Password del paso 1>` |
   | `JWT_SECRET` | Genera uno propio (ver abajo) |
   | `SPRING_PROFILES_ACTIVE` | `prod` |

   `SPRING_PROFILES_ACTIVE=prod` es importante: sin esta variable, `application.yml` cae por defecto al perfil `dev` (`show-sql`, logging debug, y una contraseña de base de datos de relleno que aquí no aplica porque ya pones la real). `prod` no tiene archivo propio, así que simplemente usa la configuración base, que es la correcta para esto.

   Para generar `JWT_SECRET` (mínimo 32 caracteres, según `ADR-002`):
   ```powershell
   -join ((48..57)+(65..90)+(97..122)|Get-Random -Count 40|%{[char]$_})
   ```

6. **Crear el primer administrador (opcional, solo para la primera vez):** agrega también `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` y `BOOTSTRAP_ADMIN_CELLPHONE`. El `AdminBootstrapRunner` solo actúa si todavía no existe ningún administrador, así que no hay riesgo de duplicar nada en despliegues posteriores. **Después de confirmar que se creó** (revisa los logs del deploy, deberías ver `Administrador inicial creado: ...`), vuelve a Environment Variables y **borra `BOOTSTRAP_ADMIN_PASSWORD`** — no dejarla ahí es la única forma de que no quede la contraseña real guardada de más.

7. **Create Web Service.** Render construye la imagen con tu `Dockerfile` y la despliega. Sigue el log del build ahí mismo.

## 4. Verificar que funcionó

Con la URL que te da Render (algo como `https://reservas-backend-xxxx.onrender.com`):

```bash
curl https://reservas-backend-xxxx.onrender.com/actuator/health
```

Debería responder `{"status":"UP"}`. Si no, revisa los logs del servicio en el dashboard — la causa casi siempre es una de las variables de entorno del paso 3.5 mal copiada (usuario, contraseña o host de la base).

Luego prueba el registro real, igual que en Postman local pero contra esta URL:

```bash
curl -X POST https://reservas-backend-xxxx.onrender.com/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Prueba Demo","email":"demo@example.com","cellphone":"3001234567","password":"Segura#2026"}'
```

## 5. Despliegues siguientes

Render se conecta directo a GitHub: cada push a `main` reconstruye y redespliega solo, sin necesidad de ningún archivo de CI. No hace falta el `github/workflows/ci.yml` que se intentó subir antes (y que además quedó en una ruta que GitHub Actions ni siquiera lee, por faltarle el punto inicial).

## Qué no cubre esta guía

- **Frontend:** no existe todavía en el repo, así que no hay nada que desplegar de ese lado.
- **HTTPS:** Render lo da automático en el dominio `.onrender.com`, no hay que configurar nada.
- **Dominio propio:** si lo necesitas más adelante, se configura en la pestaña *Settings → Custom Domains* del servicio.
