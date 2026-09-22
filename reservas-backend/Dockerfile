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

EXPOSE 8080
# Render (y la mayoría de PaaS) asignan el puerto real en $PORT en tiempo de
# ejecución; se pasa como argumento para pisar server.port sin tocar
# application.yml. Forma shell (no exec) para que se expanda la variable.
CMD java -jar app.jar --server.port=${PORT:-8080}
