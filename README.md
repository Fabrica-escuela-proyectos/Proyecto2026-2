# Plataforma de Reservas de Servicios

[![CI/CD Pipeline](https://github.com/Fabrica-escuela-proyectos/Proyecto2026-2/actions/workflows/build.yml/badge.svg)](https://github.com/Fabrica-escuela-proyectos/Proyecto2026-2/actions/workflows/build.yml)
[![Quality gate status](https://sonarcloud.io/api/project_badges/measure?project=Fabrica-escuela-proyectos_Proyecto2026-2&metric=alert_status&token=a43ea8fbb63051c3419326a17818e445fec666d6)](https://sonarcloud.io/summary/new_code?id=Fabrica-escuela-proyectos_Proyecto2026-2)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Fabrica-escuela-proyectos_Proyecto2026-2&metric=coverage&token=a43ea8fbb63051c3419326a17818e445fec666d6)](https://sonarcloud.io/summary/new_code?id=Fabrica-escuela-proyectos_Proyecto2026-2)

Backend de una plataforma para que proveedores publiquen servicios y recursos y los usuarios los reserven. Proyecto de Fábrica Escuela / CodeF@ctory (Universidad de Antioquia, 2026-2).

## Stack

- Java 17 y Spring Boot 4.1.1, monolito modular
- PostgreSQL 16 con Flyway (migraciones) y JPA/Hibernate
- Seguridad: JWT con tabla de sesiones, BCrypt, roles y MFA (TOTP) obligatorio para administradores
- Pruebas con JUnit, Mockito y Testcontainers; cobertura con JaCoCo y análisis en SonarCloud
- CI/CD con GitHub Actions y despliegue con Docker en Render

## Estructura

| Carpeta | Contenido |
|---|---|
| [`reservas-backend/`](reservas-backend) | Código de la aplicación, `Dockerfile` y `docker-compose.yml` |
| [`docs/`](docs) | Arquitectura y ADR, API, modelo de BD, guías y planes por sprint |
| [`.github/`](.github) | Pipeline de CI/CD y plantilla de reporte de bugs |

## Ejecutar en local

Requisitos: JDK 17 (con JDK 24 Lombok falla sin avisar) y Docker.

```bash
cd reservas-backend
docker compose up -d      # PostgreSQL de desarrollo
./mvnw spring-boot:run    # API en http://localhost:8080
./mvnw clean test         # pruebas (las de integración necesitan Docker)
```

Pasos detallados y variables de entorno en la [guía de desarrollo local](docs/guia-desarrollo-local.md).

## Pipeline

En cada push a `main` se ejecutan: pruebas → análisis de SonarCloud → build del JAR → despliegue en Render. Los pull requests ejecutan las mismas validaciones sin desplegar. Detalle en la [guía de despliegue](docs/guia-despliegue-render.md).

## Documentación clave

- [Arquitectura y decisiones (ADR)](docs/arquitectura)
- [Contratos y errores de la API](docs/api)
- [Plan del Sprint 2](docs/sprint-2/plan-de-trabajo-sprint-2.md) y [estado del proyecto](docs/sprint-2/handoff-contexto.md)
- [Política de MFA (ADR-004)](docs/arquitectura/adr/ADR-004-politica-mfa.md)



