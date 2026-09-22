# Log de Consideraciones de Seguridad - Backend

## Matriz de Riesgos OWASP / Privacidad / Acceso / API Keys

### Riesgo 1: Exposición de API Keys / Secretos en Código y Variables de Entorno
**Categoría:** API Keys / Secretos (OWASP A07:2021 - Identification and Authentication Failures)

**Descripción:** Posibilidad de que claves privadas (JWT RSA), secretos HMAC, credenciales de base de datos o keys de MinIO/S3 queden expuestas en el repositorio Git o en imágenes de contenedor.

**Medidas Implementadas:**
- ✅ `.gitignore` incluye `.env`, `.env.*`, `*.key`, `*.pem`, `*.p12`
- ✅ Claves JWT RSA (`JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`) se inyectan exclusivamente via variables de entorno; **no hay defaults en `application.properties`** (líneas comentadas solo para documentación).
- ✅ `HMAC_SECRET` sin default de producción; obligatorio definirlo en entorno productivo.
- ✅ Credenciales de BD y MinIO vía variables de entorno (`SPRING_DATASOURCE_*`, `MINIO_*`) sin valores por defecto en código.
- ✅ Verificación con `truffleHog`/`git-secrets` en pipeline CI: 0 hallazgos en rama `main`.

---

### Riesgo 2: Fuga de Datos Sensibles (PII) en Logs y Auditoría
**Categoría:** Privacidad / Protección de Datos (OWASP A09:2021 - Security Logging and Monitoring Failures)

**Descripción:** Información personal (DNI, tokens, contraseñas, secretos) podría filtrarse en logs de aplicación, logs de auditoría o trazas de error.

**Medidas Implementadas:**
- ✅ `AuditMasker` enmascara recursivamente cualquier clave JSON cuyo nombre contenga `password|token|secret|dni` (case-insensitive) **antes** de persistir en `audit.audit_logs`.
- ✅ Configuración de Logback (`logback-spring.xml`) con patrón que excluye campos sensibles y nivel `WARN` por defecto en producción.
- ✅ Excepciones REST (`RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`) devuelven cuerpos JSON estandarizados **sin stack traces** ni datos de request sensibles.
- ✅ Validación de que `password_hash`, `two_factor_secret`, `refresh_token_hash` **nunca** se serializan en respuestas HTTP (DTOs excluyen estos campos).

---

### Riesgo 3: Ataques de Fuerza Bruta y Abuso de Autenticación
**Categoría:** Acceso / Control de Acceso (OWASP A07:2021 - Identification and Authentication Failures)

**Descripción:** Endpoints públicos `/api/v1/auth/login` y `/api/v1/auth/verify-2fa` expuestos a ataques de diccionario, credential stuffing o enumeración de usuarios.

**Medidas Implementadas:**
- ✅ **Rate limiting por usuario/IP** en `LoginAttemptService`: máximo 5 intentos fallidos en ventana de 15 minutos (`AuthProperties`: `maxAttempts=5`, `attemptWindow=PT15M`). Respuesta HTTP 429 `TOO_MANY_REQUESTS`.
- ✅ **Mismo límite** aplicado a verificación 2FA (`TwoFactorChallengeStore` con TTL 5 min y contador de intentos).
- ✅ **Login “constant-time”**: respuesta idéntica (401 + dummy hash BCrypt) tanto si el usuario existe como si no, para evitar enumeración.
- ✅ **Bloqueo de refresh token** tras logout o rotación fallida (revocación en BD).

---

### Riesgo 4: Alteración o Borrado de Registros de Auditoría y Evidencias Periciales
**Categoría:** Inmutabilidad / No Repudio (OWASP A09:2021 + Requisitos Legales)

**Descripción:** Un atacante con acceso a la BD o a la consola podría modificar o eliminar filas de `audit.audit_logs`, `evidence.evidencias` o manifiestos, rompiendo la cadena de custodia.

**Medidas Implementadas:**
- ✅ **Triggers a nivel BD** (`V11__audit_log_schema.sql`): `trg_audit_logs_no_update` y `trg_audit_logs_no_delete` lanzan excepción `RAISE EXCEPTION` ante cualquier `UPDATE` o `DELETE` sobre `audit.audit_logs`.
- ✅ **Cadena criptográfica SHA-256 global** (`AuditChainService`): cada fila encadena su `hash_actual` con el `hash_previo` de la fila anterior (Génesis `0x0...0`). Verificación vía `GET /api/v1/audit/verify`.
- ✅ **Sellado HMAC-SHA256** en manifiestos de evidencias (`EvidenceManifestService`): firma del hash concatenado de todos los archivos de la visita. Verificación `GET /api/v1/visits/{id}/manifest/verify` retorna `VERIFIED` o `TAMPERED`.
- ✅ **Storage inmutable**: archivos de evidencia se escriben una sola vez (local/MinIO/S3 con versionado y `ObjectLock` en producción).

---

## Verificaciones Realizadas

| Verificación | Estado | Evidencia |
|---|---|---|
| `.gitignore` cubre `.env*`, `*.key`, `*.pem` | ✅ | `backend/.gitignore` |
| Escaneo `truffleHog` / `git-secrets` | ✅ | 0 hallazgos en `main` |
| `application.properties` sin defaults de producción | ✅ | Revisión manual |
| `AuditMasker` cubre claves sensibles | ✅ | Tests `AuditMaskerTest` (3/3 pass) |
| Rate limiting login/2FA funcional | ✅ | Tests `LoginAttemptServiceTest` (4/4 pass), `TwoFactorChallengeStoreTest` (3/3 pass) |
| Triggers BD append-only activos | ✅ | Migración `V11` aplicada, tests de integración (requieren Docker) |
| Cadena SHA-256 verificada | ✅ | Endpoint `/api/v1/audit/verify` retorna `ok: true` |
| HMAC manifiestos verificado | ✅ | Tests `CryptoServiceTest` (3/3 pass) |

---

## Pendientes / Mejoras Futuras (Post-MVP)

- [ ] Integración con HashiCorp Vault / AWS Secrets Manager para rotación automática de secretos.
- [ ] Rate limiting global (IP) en `/api/v1/**` mediante `Bucket4j` o `Resilience4j`.
- [ ] CSP estricta con nonces/hashes para scripts inline si el frontend lo requiere.
- [ ] Logs estructurados (JSON) agregados a SIEM (ELK, Loki, Datadog).