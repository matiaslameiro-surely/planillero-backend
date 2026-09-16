# planillero-backend

Backend de Planillero. Java 21 + Spring Boot 4.

## Requisitos

Sólo un **JDK 21**. Maven no hace falta: el proyecto trae el wrapper (`mvnw`), que se descarga la
versión correcta la primera vez.

Si tenés varios JDK instalados, apuntá `JAVA_HOME` al 21 antes de construir. En Windows, ojo con el
`java` del `PATH`: suele ser un JRE viejo, y el error que da entonces no dice que el problema es la
versión.

```bash
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"

# macOS / Linux
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
```

## Comandos

```bash
./mvnw -B test              # compilar y correr los tests
./mvnw -B -DskipTests package   # empaquetar el jar en target/
./mvnw spring-boot:run      # levantar la aplicación en http://localhost:8080
```

En Windows usá `mvnw.cmd` en lugar de `./mvnw` si no estás en Git Bash.

## Verificar que levanta

```bash
./mvnw spring-boot:run
curl http://localhost:8080/salud
# {"estado":"ok","momento":"2026-09-16T12:00:00Z"}
```

## Estructura

```
src/main/java/ar/com/planillero/
├── PlanilleroBackendApplication.java   # punto de entrada
└── salud/
    └── SaludController.java            # GET /salud
```

El paquete raíz es `ar.com.planillero`: nombrado por producto, no por empresa.

### Por qué `/salud` y no Actuator

El endpoint de salud es propio en lugar de Spring Boot Actuator. Actuator trae varios endpoints
expuestos y decisiones de seguridad que este proyecto todavía no tomó; un controller de cinco líneas
cumple lo mismo sin comprometer nada a futuro. Si más adelante hace falta métricas o readiness/liveness
para un orquestador, ahí sí conviene incorporarlo.

## Cómo se trabaja en este repo

Este repo se clona **dentro** del workspace del harness, no suelto:

```
planillero/          # repo del harness: protocolo, specs y scripts
├── backend/         # este repo
└── frontend/
```

Las tareas salen de Jira y se llevan por el ciclo que describe `AGENTS.md` en ese repo. El push
directo a `main` está bloqueado por un hook: el trabajo va en una rama `PLAN-<n>-<slug>` y entra por
pull request.
