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

## Autenticación y RBAC

El backend expone la autenticación centralizada del ecosistema:

### Endpoints de autenticación

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Login con usuario/contraseña. Devuelve access + refresh token, o `twoFactorRequired` + `challengeId` si el usuario tiene 2FA habilitado. |
| `POST` | `/api/v1/auth/verify-2fa` | Completa el segundo factor con `challengeId` y código TOTP de 6 dígitos → tokens. |
| `POST` | `/api/v1/auth/refresh` | Rota el refresh token (el anterior queda invalidado) y emite par nuevo. |
| `POST` | `/api/v1/auth/logout` | Revoca el refresh token. Idempotente. |
| `POST` | `/api/v1/auth/2fa/setup` | Emite secreto TOTP y URI `otpauth` para habilitar 2FA (Bearer). `409` si ya está habilitado. |
| `POST` | `/api/v1/auth/2fa/enable` | Confirma el código y habilita 2FA (Bearer). |
| `POST` | `/api/v1/auth/2fa/disable` | Deshabilita 2FA con código (Bearer). Idempotente si ya estaba apagado. |
| `GET` | `/api/v1/auth/me` | Usuario, roles y estado de 2FA (Bearer). |

### Características

- **JWT RS256** firmado por el backend, validado por clientes con la clave pública.
- **Access token** 15 min; **refresh token** 7 días (rotativo, hasheado en BD).
- **2FA TOTP (RFC 6238)** opcional por usuario; desafío de un solo uso (`jti` + registro en memoria con TTL).
- **Fuerza bruta**: límite de intentos en login y en verificación de 2FA (ventana temporal, 429 al superar umbral).
- **RBAC** con `@PreAuthorize`: roles `OPERATOR`, `SUPERVISOR`, `ADMINISTRATOR`.

### Base de datos

- **PostgreSQL** vía JPA + **Flyway** (migraciones versionadas).
- Esquema `core`: `users`, `roles`, `user_roles`, `refresh_tokens` (nombres en inglés por convención).
- Esquema `forms`: `form_templates` (plantillas de formulario, con el JSON Schema en JSONB).
- Esquema `visits`: `visits` (con `responses_json` en JSONB). Tabla mínima: el ciclo de vida
  completo de la visita lo agregan las tareas de agenda y geolocalización.
- Las dos columnas JSONB tienen **índice GIN**, para poder consultar por contenido del JSON sin
  recorrer la tabla entera.
- Seed de desarrollo: `operador.demo/Operador123!` (OPERATOR), `supervisor.demo/Supervisor123!` (SUPERVISOR), `admin.demo/Admin123!` (ADMINISTRATOR). **Son credenciales ficticias**, documentadas como tales.

## Formularios tipificados

La forma de cada formulario es **un dato, no código**: se publica como **JSON Schema 2020-12** en
una plantilla versionada y las respuestas se guardan en columnas **JSONB**. Agregar un campo es
publicar una versión nueva de la plantilla, no recompilar el backend.

### Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/plantillas` | Plantillas vigentes, cada una con su JSON Schema completo (Bearer). |
| `GET` | `/api/v1/plantillas/{clave}` | Última versión vigente de una plantilla. `404 template_not_found` si no existe. |
| `POST` | `/api/v1/visitas/{id}/formulario` | Valida las respuestas contra el schema y, si cumplen, las guarda. |

### Validación

- Motor: `FormSchemaValidator`, sobre `com.networknt:json-schema-validator`. Dialecto **fijo**
  2020-12 para todas las plantillas: la misma regla se comporta igual en todos los formularios.
- **Devuelve todas las violaciones**, no la primera. El `400` trae `violations[]`, con la ruta del
  campo en JSON Pointer (`/workedHours`), la palabra clave incumplida (`maximum`, `required`,
  `enum`…) y un mensaje en español.
- Los schemas compilados quedan cacheados: las plantillas son pocas e inmutables.

```bash
curl -X POST http://localhost:8080/api/v1/visitas/<uuid>/formulario \
  -H "Authorization: Bearer <jwt>" -H "Content-Type: application/json" \
  -d '{"templateKey":"mantenimiento-general",
       "responses":{"workedHours":7.5,"taskType":"CORRECTIVO","observations":"Sin novedades"}}'
# 200 {"visitId":"...","templateKey":"mantenimiento-general","templateVersion":2,"submittedAt":"..."}

# Con un campo fuera de rango y otro fuera del enum:
# 400 {"error":"form_validation_failed","message":"El formulario tiene 2 campos con problemas.",
#      "violations":[{"field":"/taskType","rule":"enum","message":"El valor debe ser uno de: ..."},
#                    {"field":"/workedHours","rule":"maximum","message":"El valor debe ser menor o igual que 24."}]}
```

### Plantillas inmutables (OWASP A03)

Una plantilla publicada **no se edita**: si se pudiera, las respuestas ya guardadas quedarían
validadas contra reglas que ya no existen. La garantía está en la base, no en la disciplina del
código:

- índice único `(template_key, version)`, y
- un trigger `before update` que rechaza cambiarle el schema, la clave, la versión o el nombre.

Dar de baja una plantilla (`active = false`) sí se permite: no cambia las reglas con las que se
validó nada.

### Claves JWT

- **No se versiona ninguna clave privada**. Si `app.jwt.private-key` / `app.jwt.public-key` están configurados (PEM), se usan; si no, desarrollo genera un par efímero en memoria y lo avisa por log.

## Verificar que levanta

```bash
./mvnw spring-boot:run
curl http://localhost:8080/health
# {"status":"UP","timestamp":"...","database":{"status":"UP","latencyMs":5}}

# Login sin 2FA (operador.demo)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"operador.demo","password":"Operador123!"}'
# {"twoFactorRequired":false,"accessToken":"<jwt>","refreshToken":"<opaco>"}

# Login con 2FA (supervisor.demo tras habilitarlo)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"supervisor.demo","password":"Supervisor123!"}'
# {"twoFactorRequired":true,"challengeId":"<jwt-breve>"}
# Luego:
curl -X POST http://localhost:8080/api/v1/auth/verify-2fa \
  -H "Content-Type: application/json" \
  -d '{"challengeId":"<el-que-devolvio-login>","code":"123456"}'
# {"accessToken":"<jwt>","refreshToken":"<opaco>"}
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
├── PlanilleroBackendApplication.java   # punto de entrada
├── auth/                               # autenticación y 2FA
│   ├── AuthController.java             # endpoints /api/v1/auth/*
│   ├── AuthService.java                # lógica: login, 2FA, refresh, logout
│   ├── TokenService.java               # emisión/validación JWT (access, challenge 2FA)
│   ├── TotpService.java                # TOTP RFC 6238
│   ├── RefreshTokenService.java        # refresh tokens hasheados, rotación, revocación
│   ├── LoginAttemptService.java        # control de fuerza bruta en memoria
│   ├── TwoFactorChallengeStore.java    # desafíos 2FA de un solo uso
│   ├── TwoFactorChallenge.java         # record (username, jti)
│   └── dto/                            # request/response del contrato
├── common/
│   ├── ApiException.java               # errores de negocio con código HTTP
│   └── ApiExceptionHandler.java        # JSON consistente {error, message}
├── health/
│   ├── HealthController.java           # GET /health y GET /salud
│   ├── HealthResponse.java             # DTO de respuesta consolidada
│   ├── DatabaseHealthService.java      # sondeo y medición de latencia de BD
│   └── DatabaseHealthResponse.java     # DTO con métricas de base de datos
├── roles/
│   ├── RoleExampleController.java      # endpoints de ejemplo por rol
│   └── ...
├── security/
│   ├── SecurityConfig.java             # Resource Server, rutas públicas, method security
│   ├── JwtConfig.java                  # claves RSA (PEM o efímeras)
│   ├── JwtProperties.java              # issuer, TTLs, claves
│   ├── AuthProperties.java             # maxAttempts, attemptWindow
│   ├── RestAuthenticationEntryPoint.java
│   └── RestAccessDeniedHandler.java
└── user/
    ├── User.java                       # entidad JPA
    ├── UserRepository.java
    ├── Role.java / RoleName.java
    └── RoleRepository.java
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