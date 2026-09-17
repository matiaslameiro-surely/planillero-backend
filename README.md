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
| `POST` | `/auth/login` | Login con usuario/contraseña. Devuelve access + refresh token, o `twoFactorRequired` + `challengeId` si el usuario tiene 2FA habilitado. |
| `POST` | `/auth/verify-2fa` | Completa el segundo factor con `challengeId` y código TOTP de 6 dígitos → tokens. |
| `POST` | `/auth/refresh` | Rota el refresh token (el anterior queda invalidado) y emite par nuevo. |
| `POST` | `/auth/logout` | Revoca el refresh token. Idempotente. |
| `POST` | `/auth/2fa/setup` | Emite secreto TOTP y URI `otpauth` para habilitar 2FA (Bearer). `409` si ya está habilitado. |
| `POST` | `/auth/2fa/enable` | Confirma el código y habilita 2FA (Bearer). |
| `POST` | `/auth/2fa/disable` | Deshabilita 2FA con código (Bearer). Idempotente si ya estaba apagado. |
| `GET` | `/auth/me` | Usuario, roles y estado de 2FA (Bearer). |

### Características

- **JWT RS256** firmado por el backend, validado por clientes con la clave pública.
- **Access token** 15 min; **refresh token** 7 días (rotativo, hasheado en BD).
- **2FA TOTP (RFC 6238)** opcional por usuario; desafío de un solo uso (`jti` + registro en memoria con TTL).
- **Fuerza bruta**: límite de intentos en login y en verificación de 2FA (ventana temporal, 429 al superar umbral).
- **RBAC** con `@PreAuthorize`: roles `OPERATOR`, `SUPERVISOR`, `ADMINISTRATOR`.

### Base de datos

- **PostgreSQL** vía JPA + **Flyway** (migraciones versionadas).
- Esquema: `users`, `roles`, `user_roles`, `refresh_tokens` (nombres en inglés por convención).
- Seed de desarrollo: `operador.demo/Operador123!` (OPERATOR), `supervisor.demo/Supervisor123!` (SUPERVISOR), `admin.demo/Admin123!` (ADMINISTRATOR). **Son credenciales ficticias**, documentadas como tales.

### Claves JWT

- **No se versiona ninguna clave privada**. Si `app.jwt.private-key` / `app.jwt.public-key` están configurados (PEM), se usan; si no, desarrollo genera un par efímero en memoria y lo avisa por log.

## Verificar que levanta

```bash
./mvnw spring-boot:run
curl http://localhost:8080/salud
# {"estado":"ok","momento":"2026-09-16T12:00:00Z"}

# Login sin 2FA (operador.demo)
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"operador.demo","password":"Operador123!"}'
# {"twoFactorRequired":false,"accessToken":"<jwt>","refreshToken":"<opaco>"}

# Login con 2FA (supervisor.demo tras habilitarlo)
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"supervisor.demo","password":"Supervisor123!"}'
# {"twoFactorRequired":true,"challengeId":"<jwt-breve>"}
# Luego:
curl -X POST http://localhost:8080/auth/verify-2fa \
  -H "Content-Type: application/json" \
  -d '{"challengeId":"<el-que-devolvio-login>","code":"123456"}'
# {"accessToken":"<jwt>","refreshToken":"<opaco>"}
```

## Estructura

```
src/main/java/ar/com/planillero/
├── PlanilleroBackendApplication.java   # punto de entrada
├── auth/                               # autenticación y 2FA
│   ├── AuthController.java             # endpoints /auth/*
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
├── user/
│   ├── User.java                       # entidad JPA
│   ├── UserRepository.java
│   ├── Role.java / RoleName.java
│   └── RoleRepository.java
└── salud/
    └── SaludController.java            # GET /salud (público)
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