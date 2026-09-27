# Guía de Despliegue en VPS Hostinger con Docker y Traefik

Esta guía documenta paso a paso cómo desplegar la plataforma **Planillero** en un VPS de Hostinger utilizando Docker Compose integrado con la infraestructura existente de **Traefik** para la terminación automática de SSL/HTTPS.

---

## 1. Arquitectura en Producción

El entorno de producción se orquesta mediante `backend/docker-compose.prod.yml` y comprende:

- **`postgres:16-alpine`**: Base de datos relacional con volumen persistente `planillero-pgdata`. Aislada en la red privada `planillero-prod-net`.
- **`backend` (Spring Boot 4.1.1 en Eclipse Temurin 21)**: API REST y lógica de negocio. Utiliza almacenamiento pericial local persistente en el volumen `planillero-storage` (`STORAGE_TYPE=local`), y claves RSA fijas montadas desde `./keys` para persistencia de sesiones.
- **`backoffice` (Angular 22 + NGINX unprivileged)**: Interfaz web de administración. Expone la SPA y enruta llamadas a `/api/` y `/health` hacia el backend en el mismo origen.
- **Traefik (en red `pigar-staging_edge`)**: Proxy inverso perimetral del VPS que detecta las etiquetas de `backoffice`, resuelve el certificado HTTPS con Let's Encrypt y enruta el tráfico desde `planillero.ferchamorro.cloud`.

---

## 2. Paso Previo: Registro DNS

En el panel de administración donde tengas delegada la zona DNS de `ferchamorro.cloud`:
1. Crear un registro:
   - **Tipo**: `A`
   - **Nombre / Host**: `planillero`
   - **Valor / Destino**: `<IP_PUBLICA_DEL_VPS>`
   - **TTL**: 1 minuto (o automático)
2. *Nota si usás Cloudflare*: Dejar el registro en modo **Solo DNS (nube gris)** para que el challenge de Let's Encrypt de Traefik no sea interceptado por el proxy de Cloudflare.

---

## 3. Preparación en el VPS

Conectate por SSH a tu VPS de Hostinger y cloná (o actualizá) el repositorio:

```bash
cd /opt  # o tu carpeta habitual de proyectos
git clone https://github.com/matiaslameiro-surely/planillero.git
cd planillero
git checkout main
```

*(Si desplegás clonando repos por separado, asegurate de que `backend` y `backoffice` se encuentren al mismo nivel).*

---

## 4. Generación de Claves JWT y Variables de Entorno

Ingresá a la carpeta `backend`:

```bash
cd backend

# 1. Generar par de claves RSA 2048 para JWT y secreto HMAC
chmod +x scripts/*.sh
./scripts/generate-jwt-keys.sh
```

El script creará `keys/private.pem` y `keys/public.pem` con permisos restringidos, y mostrará en pantalla un secreto HMAC sugerido.

A continuación, creá tu archivo `.env.production`:

```bash
cp .env.production.example .env.production
nano .env.production
```

Definí valores seguros:
- `POSTGRES_PASSWORD`: contraseña segura para la base de datos de producción.
- `HMAC_SECRET`: pegá el hash generado en el paso anterior.
- Verificá que `APP_DOMAIN=planillero.ferchamorro.cloud`.

---

## 5. Puesta en Marcha del Contenedor

Para construir y levantar los contenedores en segundo plano:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
```

Podés verificar el estado de los servicios con:

```bash
docker compose -f docker-compose.prod.yml ps
```

Los tres servicios (`postgres`, `backend`, `backoffice`) deben figurar en estado `healthy` o `running`.

---

## 6. Verificación Automática

Una vez levantado y tras unos segundos para que Traefik emita el certificado TLS:

```bash
./scripts/verify-deployment.sh https://planillero.ferchamorro.cloud
```

El script verificará:
1. Acceso web al Backoffice (código HTTP 200).
2. Respuesta del endpoint `/health`.
3. Presencia de cabeceras de seguridad (CSP, HSTS, X-Frame-Options, X-Content-Type-Options).
4. Inicio de sesión funcional contra la API con el usuario de demostración (`admin.demo`).

---

## 7. Credenciales de Demostración para el Informe Final

Para la entrega académica, las migraciones automáticas de Flyway incluyen los siguientes usuarios y datos ficticios ya listos para operar:

| Usuario | Contraseña | Rol | Jurisdicción | Caso de uso para el docente |
|---|---|---|---|---|
| `admin.demo` | `Admin123!` | Administrador | Global | Gestión completa, auditoría, configuración |
| `supervisor.demo` | `Supervisor123!` | Supervisor | ZONA_NORTE | Planificación de rutas y supervisión de visitas |
| `operador.demo` | `Operador123!` | Operador | ZONA_NORTE | Carga de planillas y ejecución de visitas |

---

## 8. Generación del APK Móvil

Para la app móvil Android apuntando a tu entorno publicado:

```bash
cd ../frontend
chmod +x scripts/build-apk.sh
./scripts/build-apk.sh https://planillero.ferchamorro.cloud
```

El APK resultante quedará listo para instalarse en cualquier dispositivo Android o emulador para la prueba de campo y video demostrativo.
