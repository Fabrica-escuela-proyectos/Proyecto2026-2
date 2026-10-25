# Guía: recuperar el despliegue cuando el plan gratuito de Render se agota

Estado a **2026-10-24**: el servicio `https://proyecto2026-2-5zoo.onrender.com` **no responde** (la conexión se abre y la petición no recibe respuesta). Los pipelines de `main` siguen pasando pruebas, Sonar y build; lo único que falla es el despliegue.

## 1. Qué pasó (verificado y no verificado)

| Hecho | Estado |
|---|---|
| Desde el 24-oct, seis ejecuciones de `main` (#48 a #54, las fusiones de Dependabot) quedaron **colgadas en «Wait for application health»** | Verificado por la API de GitHub |
| La última ejecución con despliegue sano fue la **#47 (16-oct)** | Verificado |
| Causa de que **se cuelguen en lugar de fallar**: el `curl` del *smoke test* no tenía tiempo límite y Render acepta la conexión pero no responde | Verificado en el workflow; **corregido** (ver §3) |
| Render gratuito: **750 horas de instancia por mes y por espacio de trabajo**; al agotarse **suspende todos los servicios gratuitos hasta el mes siguiente** | Documentación oficial de Render |
| Base de datos gratuita de Render: **expira a los 30 días**, hay **14 días de gracia** y luego **Render la borra con sus datos** | Documentación oficial de Render |
| Cuál de los dos límites (horas o base de datos) fue el que se agotó en su caso | **No lo puedo ver**: solo el panel de Render lo dice |

> **Corrección de nuestra documentación:** varias guías decían que la base gratuita «expira a los 90 días (≈ 21 de diciembre)». Eso era un supuesto mío y **está desactualizado: hoy son 30 días**. Ya están corregidas.
> Las 750 horas se **comparten entre todos los servicios gratuitos del mismo espacio de trabajo** (si BANAPP u otro proyecto está en la misma cuenta de Render, cuenta para el mismo tope).

## 2. Diagnóstico en 2 minutos (panel de Render)

| Qué ves | Qué significa | Siguiente paso |
|---|---|---|
| El web service dice **Suspended** (por horas gratuitas) | El tope de 750 h se agotó; vuelve a empezar el **1 de noviembre** | Esperar, o pasar solo el servicio a un plan de pago el tiempo que haga falta |
| La base de datos dice **Expired** | Pasaron 30 días. **Tiene 14 días de gracia**: pasado ese plazo se borra | Si necesitas esos datos: actualizar a un plan de pago **antes** del vencimiento de la gracia. Si no: crear una nueva (ver §4) |
| Ambos | Lo más probable | Combinar ambas acciones |
| El servicio figura «Live» pero no responde | Algo más (arranque fallido); revisa **Logs** y **Events** | Pega aquí el final del log |

## 3. Hoy mismo (10 minutos, sin gastar nada)

1. **Cancela las 6 ejecuciones colgadas de `main`** (#48, #49, #50, #52, #53 y #54): GitHub → *Actions* → abrir cada una → **Cancel workflow**. No pueden terminar bien.
2. **Trae lo último y haz commit** de la corrección del workflow (`.github/workflows/build.yml`): ahora el despliegue tiene `timeout-minutes: 15`, el `curl` lleva `-m 15` y las fusiones seguidas no apilan despliegues (`concurrency`).
3. **Mientras no haya dónde desplegar**, pon la variable `DEPLOY_ENABLED = false` (*Settings → Secrets and variables → Actions → Variables*). El trabajo «Deploy to Render» se omite y el pipeline queda en verde. Cuando haya hosting, **bórrala o ponla en `true`**.
4. Los PR de Dependabot ya fusionados pasaron pruebas, Sonar y build: lo único que falló es el despliegue, no el código.

## 4. Opciones para volver a tener la aplicación en línea

| Opción | Costo | Esfuerzo | Mantiene URL, Swagger y pipeline | Comentario |
|---|---|---|---|---|
| **A. Render de pago** (web service + base de datos) | Unos US$ 13/mes en total *(precio por verificar en render.com/pricing)* | Mínimo | **Sí, todo igual** | Lo más rápido y estable; se puede dividir entre el equipo y cancelar después |
| **B. Render (cómputo) + Neon (base de datos)** | Gratis | Bajo (≈ 30 min) | **Sí** (mismo servicio y URL) | **Recomendada.** La base de Neon no expira a los 30 días. El cómputo sigue sujeto a las 750 h/mes |
| C. Cloud Run (Google) + Neon | Gratis dentro de la capa gratuita; exige tarjeta | Medio (2–3 h: cuenta, imagen, pipeline) | No (URL nueva y cambios en `build.yml`) | Sin tope mensual de horas, pero arranque en frío de la JVM |
| D. Azure para estudiantes (US$ 100 de crédito por 12 meses) + Neon | Sale del crédito | Medio | No | Encaja con Azure DevOps y las cuentas `@udea.edu.co`; verifica qué servicios cubre el crédito |
| E. Local + túnel (`docker compose` y un túnel público) | Gratis | Bajo, pero **el equipo debe estar encendido** | No (URL temporal) | Sirve para sesiones puntuales de pruebas o una demostración |

**Recomendación:** **B** si pueden esperar al 1 de noviembre para el cómputo (o pagar un mes de plan Starter solo para el servicio si lo necesitan antes), y **A** si necesitan que quede siempre arriba para Calidad durante el Sprint 3. No recomiendo Koyeb: las fuentes no coinciden sobre si todavía ofrece un servicio web gratuito.

## 5. Procedimiento de la opción B (Render + Neon)

Qué **no** cambia: el código, el `Dockerfile`, el pipeline, la URL, el Swagger.

1. **Crear la base en Neon** ([neon.com](https://neon.com), plan gratuito: 0,5 GB, 100 horas de cómputo al mes, se duerme a los 5 minutos de inactividad). Elige la región más cercana a la de Render. Copia host, base, usuario y contraseña.
2. **Habilitar la extensión** que usa el anti-overbooking: en el editor SQL de Neon, `CREATE EXTENSION IF NOT EXISTS btree_gist;` (la migración V10 también la crea; Neon documenta esta extensión).
3. **En Render** (el web service existente → *Environment*), actualiza:

   | Variable | Valor |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://<host directo de Neon>/<base>?sslmode=require` — usa el host **sin `-pooler`** y **quita** de la cadena de Neon el parámetro `channel_binding` |
   | `DB_USERNAME` / `DB_PASSWORD` | Los de Neon |
   | `SPRING_DATASOURCE_HIKARI_MAX_LIFETIME` | `240000` (4 min: evita conexiones viejas cuando Neon se duerme) |
   | `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` | `0` |
   | `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, `BOOTSTRAP_ADMIN_CELLPHONE` | El primer administrador (ver §6) |
   | `SPRING_PROFILES_ACTIVE`, `JWT_SECRET`, `SWAGGER_ENABLED` | **Los que ya tenía** (`prod`, la clave propia y `true` para el Swagger) |

4. **Redespliega** (Manual Deploy en Render o el *deploy hook*). Flyway crea las 12 migraciones solo. Comprueba `GET /actuator/health` (puede tardar la primera vez), `/swagger-ui.html` y `/v3/api-docs`.
5. **Enrolar el MFA del administrador** (`/auth/mfa/setup` + `/activate`, guía en `cierre-pendientes-sprint-1.md` §2.6) y, después, quitar `BOOTSTRAP_ADMIN_PASSWORD` de las variables.
6. **Probar:** `npx newman run docs/postman/Reservas-Sprint2.postman_collection.json -e docs/postman/Reservas-Render.postman_environment.json --timeout-request 150000` (carpetas 00–07 sin administrador).
7. **Reactivar el pipeline:** borra la variable `DEPLOY_ENABLED` (o ponla en `true`).

**No lo he probado contra Neon.** Lo que sí está verificado: Neon documenta `btree_gist`, la aplicación arranca con cualquier PostgreSQL 16 con esas variables (así corre en las pruebas y en local), y las pruebas de la propia aplicación cubren las migraciones.

## 6. Qué se pierde y qué aprovechar

- **Se pierden los datos** de la base anterior: usuarios de prueba, negocios, reservas, los dos administradores y sus secretos MFA. Los administradores hay que **crearlos de nuevo** (y quien tenga celular, volver a enrolar su MFA); el segundo administrador se vuelve a ascender con `PATCH /users/{id}/role`.
- **Aprovecha la base nueva para cerrar SEC-01/02:** el administrador demo anterior usaba una contraseña **publicada en las guías**. Con la base nueva, elige una contraseña **nueva que nunca se escriba en el repositorio** y quita la antigua de `guia-prueba-aplicacion-desplegada.md`.
- Si la base vieja todavía está dentro de los 14 días de gracia y quieren **conservar los datos**, hay que pasarla a un plan de pago antes de que se cumplan (después Render la borra); si no importan, no hace falta.

## 7. Verificación final

| Qué | Cómo |
|---|---|
| La aplicación responde | `curl https://proyecto2026-2-5zoo.onrender.com/actuator/health` → `{"status":"UP"}` |
| El Swagger carga | Abrir `/swagger-ui.html` |
| El pipeline despliega | Un commit a `main` y el job «Deploy to Render» termina en verde (con la base nueva **y** el servicio activos) |
| La extensión y las migraciones | `select count(*) from flyway_schema_history;` → 12 filas |

Fuentes: [documentación de planes gratuitos de Render](https://render.com/docs/free) · [extensión `btree_gist` en Neon](https://neon.com/docs/extensions/btree_gist) · [planes y límites de Neon](https://neon.com/pricing).
