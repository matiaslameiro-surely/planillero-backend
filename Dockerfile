# syntax=docker/dockerfile:1

# ---- Etapa de build: JDK completo, sólo para compilar ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Primero lo que cambia poco (wrapper y pom) para que la resolución de dependencias quede en una
# capa aparte y se reuse mientras no cambie el pom. `sh mvnw` porque el bit de ejecución no está
# garantizado según cómo se haya hecho el checkout.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q dependency:go-offline

COPY src src
# Los tests corren en los gates del repo; la imagen sólo empaqueta.
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q -DskipTests package \
    && mv target/planillero-backend-*.jar /workspace/app.jar

# ---- Etapa de ejecución: sólo JRE, sin herramientas de compilación ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario sin privilegios (OWASP A06): el proceso nunca corre como root.
RUN addgroup -S planillero && adduser -S -G planillero planillero \
    && mkdir -p /app/storage \
    && chown planillero:planillero /app/storage

COPY --from=build --chown=planillero:planillero /workspace/app.jar /app/app.jar

USER planillero
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
