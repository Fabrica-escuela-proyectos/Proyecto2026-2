# JDK 17 obligatorio: Lombok no genera código (builder/getters) con JDK 24+
# en este proyecto (ver docs/estado-proyecto-sprint-1.md). No subir la
# imagen base a "latest" sin volver a verificar esa incompatibilidad.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src ./src
# Sin tests: los de integración necesitan Docker (Testcontainers), que no
# está disponible dentro de este build.
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# SEC-05 (OWASP A05): la aplicación no corre como root. Una vulnerabilidad en ella (o en una dependencia)
# no debería dar un shell con privilegios dentro del contenedor.
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
USER app

EXPOSE 8080
# Marca el contenedor como «unhealthy» si /actuator/health deja de responder (la ruta es pública; no expone
# detalles). start-period cubre el arranque de la JVM y las migraciones de Flyway.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD curl -fsS "http://localhost:${PORT:-8080}/actuator/health" > /dev/null || exit 1
# Render (y la mayoría de PaaS) asignan el puerto real en $PORT en tiempo de
# ejecución; se pasa como argumento para pisar server.port sin tocar
# application.yml. Forma shell (no exec) para que se expanda la variable.
CMD java -jar app.jar --server.port=${PORT:-8080}
