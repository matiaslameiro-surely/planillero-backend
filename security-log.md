# Log de Consideraciones de Seguridad - Backend

## Matriz de Riesgos OWASP / Privacidad / Acceso / API Keys

### Riesgo 1: Exposición de API Keys / Secretos en Código y Variables de Entorno
**Categoría:** API Keys / Secretos (OWASP A07:2021 - Identification and Authentication Failures)

**Descripción:** Posibilidad de que claves privadas (JWT RSA), secretos HMAC, credenciales de base de datos o keys de MinIO/S3 queden expuestas en el repositorio Git o en imágenes de contenedor.

**Medidas Implementadas:**
- ✅ `.gitignore` incluye `.env`, `.env.*`, `!.env.example`, `*.key`, `*.pem`, `*.p12`.
- ✅ Claves JWT RSA (`JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`) se inyectan exclusivamente via variables de entorno; **no hay defaults en `application.properties`** (líneas comentadas solo para documentación; en dev la app genera un par efímero en memoria).
- ⚠️ **Defaults solo de desarrollo**: `SPRING_DATASOURCE_PASSWORD:planillero`, `MINIO_ACCESS_KEY/SECRET_KEY:minioadmin` y `HMAC_SECRET:dev-secret-key-…` existen en `application.properties` **únicamente para facilitar el arranque local**. Producción **debe** sobreescribirlos por variables de entorno; si los deja, arranca con secretos conocidos. Pendiente: fallar al arrancar en `prod` si no están definidos.
- ✅ Verificación manual de secretos no versionados: `git ls-files` y `git log` sobre `.env*`, `*.key`, `*.pem`, `*.p12` → **0 archivos sospechosos** en árbol ni historial.

---

### Riesgo 2: Fuga de Datos Sensibles (PII) en Logs y Auditoría
**Categoría:** Privacidad / Protección de Datos (OWASP A09:2021 - Security Logging and Monitoring Failures)

**Descripción:** Información personal (DNI, tokens, contraseñas, secretos) podría filtrarse en logs de aplicación, logs de auditoría o trazas de error.

**Medidas Implementadas:**
- ✅ `AuditMasker` enmascara recursivamente cualquier clave JSON cuyo nombre contenga `password|token|secret|dni` (case-insensitive) **antes** de persistir en `audit.audit_logs`.
- ✅ Excepciones REST (`RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`) devuelven cuerpos JSON estandarizados **sin stack traces** ni datos de request sensibles.
- ✅ Validación de que `password_hash`, `two_factor_secret`, `refresh_token_hash` **nunca** se serializan en respuestas HTTP (DTOs excluyen estos campos).

---

### Riesgo 3: Ataques de Fuerza Bruta y Abuso de Autenticación
**Categoría:** Acceso / Control de Acceso (OWASP A07:2021 - Identification and Authentication Failures)

**Descripción:** Endpoints públicos `/api/v1/auth/login` y `/api/v1/auth/verify-2fa` expuestos a ataques de diccionario, credential stuffing o enumeración de usuarios.

**Medidas Implementadas:**
- ✅ **Rate limiting por usuario** en `LoginAttemptService`: máximo 5 intentos fallidos en ventana de 15 minutos (`AuthProperties`: `maxAttempts=5`, `attemptWindow=PT15M`). La clave es el `username` (y `2fa:<username>` para el challenge), no la IP. Respuesta HTTP 429 `TOO_MANY_REQUESTS`.
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
- ✅ **Storage WORM local**: los archivos de evidencia se escriben una sola vez en disco (`LocalStorageService`; `STORAGE_TYPE=local`). Una escritura a una clave existente lanza `WormPolicyViolationException`.

---

### Riesgo 5: Acceso Horizontal a Visitas Ajenas
**Categoría:** Acceso / Control de Acceso (OWASP A01:2021 - Broken Access Control)

**Descripción:** Un usuario autenticado con un rol habilitado podía leer o escribir evidencias, manifiestos y formularios de visitas de otra zona o que no le asignaron. El `@PreAuthorize` sólo controla el rol, y cada endpoint recibe el identificador de la visita del cliente (PLAN-49).

**Medidas Implementadas:**
- ✅ **Recorte por jurisdicción en planificación** (`PlanningService`): el supervisor sólo lista, consulta y asigna visitas y operadores de su zona (`403 outside_jurisdiction`).
- ✅ **Inicio de visita sólo por su operador** (`VisitStartService`): la visita tiene que estar en una hoja de ruta propia (`403 visit_not_assigned`).
- ✅ **Guardia por visita** (`VisitAccessGuard`): evidencias, manifiesto, formulario y sincronización diferida exigen que la visita sea del usuario. El administrador accede a todas, el supervisor a las de su jurisdicción y el operador a las de su hoja de ruta. La guardia corre antes de validar o escribir nada, así que un rechazo no deja binarios en el storage WORM ni filas en la base. En el sync se rechaza sólo la operación afectada, y el control va antes de detectar repetidas.
- ⚠️ **Existencia observable**: una visita inexistente responde `404` y una ajena, `403`. Los identificadores son UUID v4 y no se pueden enumerar.

---

## Verificaciones Realizadas

| Verificación | Estado | Evidencia |
|---|---|---|
| `.gitignore` cubre `.env`, `.env.*`, `!.env.example`, `*.key`, `*.pem`, `*.p12` | ✅ | `backend/.gitignore` |
| Escaneo manual de secretos (`.env*`, `*.key`, `*.pem`, `*.p12`) | ✅ | `git ls-files` + `git log`: 0 archivos en árbol ni historial |
| `application.properties`: defaults documentados como dev-only | ✅ | Revisión manual (`SPRING_DATASOURCE_PASSWORD`, `MINIO_*`, `HMAC_SECRET`) |
| `AuditMasker` cubre claves sensibles | ✅ | Tests `AuditMaskerTest` (3/3 pass) |
| Rate limiting login/2FA funcional | ✅ | Tests `LoginAttemptServiceTest` (4/4 pass), `TwoFactorChallengeStoreTest` (3/3 pass) |
| Triggers BD append-only activos | ✅ | Migración `V11` aplicada, tests de integración (requieren Docker) |
| Cadena SHA-256 verificada | ✅ | Endpoint `/api/v1/audit/verify` retorna `ok: true` |
| HMAC manifiestos verificado | ✅ | Tests `CryptoServiceTest` (3/3 pass) |
| Acceso horizontal por visita (zona ajena, no asignada, roles legítimos) | ✅ | Tests `VisitAccessIntegrationTest` (requieren Docker) |

---

## Pendientes / Mejoras Futuras (Post-MVP)

- [ ] Integración con HashiCorp Vault / AWS Secrets Manager para rotación automática de secretos.
- [ ] Rate limiting global (IP) en `/api/v1/**` mediante `Bucket4j` o `Resilience4j`.
- [ ] CSP estricta con nonces/hashes para scripts inline si el frontend lo requiere.
- [ ] Logs estructurados (JSON) agregados a SIEM (ELK, Loki, Datadog).