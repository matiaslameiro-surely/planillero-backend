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
curl http://localhost:8080/health
# {"status":"UP","timestamp":"...","database":{"status":"UP","latencyMs":5}}
```

También responde en `/salud` por retrocompatibilidad con clientes existentes.

## Configuración y Persistencia

La aplicación utiliza PostgreSQL 16 gestionado mediante migraciones automáticas con Flyway y pool de conexiones HikariCP.

Las credenciales de conexión se configuran mediante variables de entorno (OWASP A05):

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC de conexión a PostgreSQL | `jdbc:postgresql://localhost:5432/planillero` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de base de datos | `planillero` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña del usuario | `planillero` |

### Esquemas lógicos y extensiones

Flyway ejecuta al inicio las migraciones ubicadas en `src/main/resources/db/migration/`:
- Habilita la extensión `pgcrypto` para identificadores UUID y hashing.
- Crea los esquemas lógicos del sistema: `core`, `visits`, `forms`, `audit` (con alias secundarios `visitas`, `formularios`, `auditoria`).

## Estructura

```
src/main/java/ar/com/planillero/
├── PlanilleroBackendApplication.java       # punto de entrada
└── health/
    ├── HealthController.java               # GET /health y GET /salud
    ├── HealthResponse.java                 # DTO de respuesta consolidada
    ├── DatabaseHealthService.java          # sondeo y medición de latencia de BD
    └── DatabaseHealthResponse.java         # DTO con métricas de base de datos
```

El paquete raíz es `ar.com.planillero`: nombrado por producto, no por empresa. Todo el código (clases, métodos, variables) se escribe en inglés; los comentarios explicativos y documentación en español.

### Por qué `/health` propio y no Actuator

El endpoint de salud es propio en lugar de Spring Boot Actuator. Actuator trae varios endpoints
expuestos y decisiones de seguridad que este proyecto todavía no tomó; un servicio liviano inyectado
en el controller cumple con la visibilidad del estado del sistema (Heurística 1 de UX) y mide la latencia real
hacia PostgreSQL sin comprometer nada a futuro.

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
