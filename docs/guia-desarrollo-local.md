# Guía de desarrollo local

Para cualquier persona del equipo que va a clonar el proyecto, correrlo en su máquina y empezar a hacer cambios. Cubre: clonar, correr contra un Postgres nativo (sin Docker), y el flujo de Git para no repetir el incidente de "Add files via upload" que rompió `main` el 2026-09-13.

## 1. Requisitos previos

- **Git**.
- **JDK 17** (no una versión más nueva — hay una incompatibilidad conocida entre Lombok y JDKs muy recientes como el 24; JDK 17 es la que usa el equipo y coincide con `<java.version>` del `pom.xml`). Instalar con:
  ```powershell
  winget install Microsoft.OpenJDK.17
  ```
- **PostgreSQL** instalado (16 o 17). Esta guía asume que **no** quieres usar Docker.
- Un IDE (IntelliJ IDEA, VS Code, etc.) — opcional, también se puede correr todo por terminal.
- **No hace falta instalar Maven aparte**: el repo trae `mvnw`/`mvnw.cmd` (Maven Wrapper), que descarga la versión correcta solo.

## 2. Clonar el proyecto

```bash
git clone https://github.com/Fabrica-escuela-proyectos/Proyecto2026-2.git
cd Proyecto2026-2/reservas-backend
```

## 3. Configurar Postgres nativo (sin Docker)

La app solo necesita un Postgres alcanzable con un rol y una base específicos — no le importa si es nativo o en contenedor. Flyway crea las tablas solo en el primer arranque, **no hay que crear tablas a mano**.

1. Conéctate a tu Postgres como superusuario (con `psql` o pgAdmin) y crea el rol y la base que la app espera:
   ```sql
   CREATE ROLE reservas_app WITH LOGIN PASSWORD 'elige-una-contraseña';
   CREATE DATABASE reservas OWNER reservas_app;
   ```
2. Define la variable de entorno `DB_PASSWORD` con esa misma contraseña antes de correr la app:
   - **Terminal (PowerShell):**
     ```powershell
     $env:DB_PASSWORD = "elige-una-contraseña"
     $env:JAVA_HOME = "<ruta a tu JDK 17>"
     ./mvnw spring-boot:run
     ```
   - **IntelliJ:** Run → Edit Configurations... → `ReservasBackendApplication` → *Environment variables* → agregar `DB_PASSWORD=elige-una-contraseña`. Y asegúrate de que el **Project SDK** del módulo sea JDK 17.
   - Si tu Postgres usa un puerto, usuario o nombre de base distintos a los default (`localhost:5432`, `reservas_app`, `reservas`), también define `DB_URL` y `DB_USERNAME` (ver `.env.example` en la raíz de `reservas-backend/` para el formato).
3. Corre la app. Deberías ver en el log a Flyway migrando el esquema y Tomcat arrancando en el puerto 8080:
   ```
   Migrating schema "public" to version "1 - create identity schema"
   Migrating schema "public" to version "2 - create sessions table"
   Tomcat started on port 8080 (http)
   ```

## 4. Flujo de Git para hacer y subir cambios

**Regla de oro: nunca usar "Add files via upload" en la web de GitHub.** No hace un merge real con git, no corre nada localmente (ni compila, ni corre tests), y fácilmente sobrescribe o borra archivos de otras personas sin que te enteres — eso es justo lo que rompió `main` hace poco (se perdió una dependencia del `pom.xml` y se filtró una contraseña real de base de datos en un archivo). Todo cambio se hace con `git` desde tu terminal o tu IDE.

### Antes de empezar a trabajar

```bash
git checkout main
git pull
```

### Crear una rama para tu tarea

Nunca commitees directo en `main`. Crea una rama descriptiva:

```bash
git checkout -b feature/nombre-de-tu-tarea
```

Ejemplos: `feature/hu02-login`, `fix/validacion-celular`.

### Hacer y revisar tus cambios

```bash
git status        # qué archivos cambiaron
git diff           # qué cambió exactamente, línea por línea
```

Revisa siempre `git status`/`git diff` antes de agregar — así evitas subir un `application-local.yml` con tu contraseña, archivos de build (`target/`), etc.

### Agregar y commitear

```bash
git add ruta/al/archivo1 ruta/al/archivo2
git commit -m "Mensaje claro: qué cambia y por qué"
```

Evita `git add .`/`git add -A` sin mirar antes qué vas a incluir. Haz commits pequeños y frecuentes, no uno gigante al final.

### Subir tu rama (no `main`)

```bash
git push -u origin feature/nombre-de-tu-tarea
```

### Abrir un Pull Request

En GitHub, abre un PR desde tu rama hacia `main` para que alguien del equipo revise antes de fusionar. Así cualquier error (como el de credenciales filtradas) se detecta antes de llegar a `main`, no después.

### Después de que se fusione tu PR

```bash
git checkout main
git pull
git branch -d feature/nombre-de-tu-tarea
```

## 5. Comandos de git de referencia rápida

| Comando | Para qué sirve |
|---|---|
| `git status` | Ver qué archivos cambiaron |
| `git diff` | Ver el contenido exacto de los cambios sin commitear |
| `git log --oneline -10` | Ver los últimos 10 commits |
| `git pull` | Traer los cambios nuevos de `main` |
| `git stash` / `git stash pop` | Guardar cambios sin commitear temporalmente (para cambiar de rama sin perderlos) |
| `git branch` | Ver en qué rama estás y cuáles existen localmente |

## 6. Qué nunca hacer

- No usar "Add files via upload" en GitHub.
- No commitear contraseñas, tokens o cadenas de conexión reales en ningún archivo (`docker-compose.yml`, `application.yml`, etc.) — siempre vía variables de entorno.
- No hacer `git push --force` sobre `main`.
- No commitear directo a `main`: siempre rama + Pull Request.
