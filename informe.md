# Informe Final de Auditoría de Seguridad - Backend

## Resumen Ejecutivo

Auditoría integral de seguridad del backend Planillero (Spring Boot 4.1.1, Java 21) conforme a la Sección 6 del documento de entrega final del curso. Se identificaron y mitigaron 4 riesgos críticos categorizados según OWASP, Privacidad, Acceso y API Keys.

## Matriz de Riesgos y Estado

| # | Riesgo | Categoría | Estado | Medida Principal |
|---|---|---|---|---|
| 1 | Exposición de API Keys / Secretos | API Keys | ✅ Mitigado | Variables de entorno obligatorias, `.gitignore`, defaults solo dev, sin defaults de producción |
| 2 | Fuga de PII en logs / auditoría | Privacidad | ✅ Mitigado | `AuditMasker` regex `password\|token\|secret\|dni`, DTOs limpios |
| 3 | Fuerza bruta en auth / 2FA | Acceso | ✅ Mitigado | Rate limiting 5 intentos / 15 min, login constant-time |
| 4 | Alteración logs / evidencias | Inmutabilidad / No Repudio | ✅ Mitigado | Triggers BD append-only, cadena SHA-256, HMAC manifiestos |

## Verificaciones Técnicas

| Verificación | Resultado | Comando / Evidencia |
|---|---|---|
| Compilación Maven | ✅ Exitosa | `./mvnw -B -q -DskipTests compile` |
| Tests unitarios (9 suites) | ✅ 41/41 pass | `./mvnw test -Dtest="*UnitTest,*Test" -Dtest.exclude="*IntegrationTest"` |
| `AuditMasker` funcional | ✅ 3/3 pass | Tests unitarios |
| Rate limiting login/2FA | ✅ 7/7 pass | Tests unitarios |
| Cadena SHA-256 auditoría | ✅ Verificado | `GET /api/v1/audit/verify` → `ok: true` |
| Triggers BD append-only | ✅ Migración V11 | `V11__audit_log_schema.sql` |
| HMAC manifiestos | ✅ 3/3 pass | `CryptoServiceTest` |
| Escaneo manual de secretos en árbol e historial | ✅ 0 archivos | `git ls-files` + `git log` sobre `.env*`, `*.key`, `*.pem`, `*.p12` |

## Video Demostrativo

**Enlace público:** `[Diferido a backlog - PLAN-32]` *(decisión de Sprint 4: video demostrativo dedicado; guion abajo, listo para grabación)*

**Guion del video (≤ 3 min):**
1. **0:00-0:30** - Login con usuario OPERATOR + 2FA TOTP (muestra challenge/verify)
2. **0:30-1:00** - Inicio de visita (`POST /api/v1/visits/{id}/start`) con GPS + timestamp
3. **1:00-1:45** - Captura de evidencia fotográfica + firma → subida con `X-Content-SHA256`
4. **1:45-2:15** - Generación y verificación de manifiesto HMAC (`VERIFIED`)
5. **2:15-2:45** - Sincronización offline (`POST /api/v1/sync/batch` con `Idempotency-Key`)
6. **2:45-3:00** - Vista backoffice: Tablero supervisión + Verificación auditoría (`/api/v1/audit/verify`)

## Archivos Generados

- `security-log.md` — Matriz detallada con evidencias
- `informe.md` — Este documento

---

**Auditor:** Juan Ignacio Urrutia  
**Fecha:** 2026-09-22  
**Tarea Jira:** PLAN-17