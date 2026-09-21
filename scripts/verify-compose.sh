#!/bin/sh
# Verifica el entorno integral de punta a punta: lo levanta, espera a que los servicios estén
# `healthy`, prueba con curl y lo baja. Sale con código distinto de 0 ante cualquier falla, así que
# sirve tal cual como paso de CI.
#
# Usa un proyecto de Compose y puertos propios, así que NO toca un entorno de desarrollo que tengas
# levantado (ni sus volúmenes: el `down -v` del final sólo borra los de este proyecto).
#
# Variables opcionales:
#   ENV_FILE  archivo de variables (por defecto .env si existe, si no .env.example)
#   TIMEOUT   segundos máximos de espera a `healthy` (por defecto 300)
#   KEEP_UP   si vale 1, no baja el entorno al terminar (para inspeccionarlo a mano)

set -u

cd "$(dirname "$0")/.." || exit 1

if [ -z "${ENV_FILE:-}" ]; then
  if [ -f .env ]; then ENV_FILE=.env; else ENV_FILE=.env.example; fi
fi
TIMEOUT="${TIMEOUT:-300}"

export COMPOSE_PROJECT_NAME=planillero-verify
# Puertos distintos de los de desarrollo para no chocar con un entorno ya levantado.
export POSTGRES_PORT=15432 MINIO_API_PORT=19000 MINIO_CONSOLE_PORT=19001 BACKEND_PORT=18080 WEB_PORT=18081

compose() { docker compose --env-file "$ENV_FILE" "$@"; }

fail() {
  echo "FALLO: $1" >&2
  echo "--- estado de los servicios ---" >&2
  compose ps -a >&2
  echo "--- últimos logs ---" >&2
  compose logs --tail 40 >&2
  exit 1
}

cleanup() {
  if [ "${KEEP_UP:-0}" = "1" ]; then
    echo "KEEP_UP=1: el entorno queda levantado (proyecto $COMPOSE_PROJECT_NAME)."
  else
    compose down -v --remove-orphans >/dev/null 2>&1
  fi
}
trap cleanup EXIT

echo "==> Levantando el entorno (env-file: $ENV_FILE)"
compose up --build -d || fail "docker compose up"

echo "==> Esperando a que los servicios estén healthy (máx. ${TIMEOUT}s)"
SERVICES="postgres minio backend backoffice"
elapsed=0
while :; do
  pending=""
  for svc in $SERVICES; do
    cid=$(compose ps -q "$svc")
    [ -n "$cid" ] || fail "el servicio $svc no existe"
    status=$(docker inspect --format '{{.State.Health.Status}}' "$cid" 2>/dev/null)
    case "$status" in
      healthy) ;;
      unhealthy) fail "el servicio $svc quedó unhealthy" ;;
      *) pending="$pending $svc" ;;
    esac
  done
  [ -z "$pending" ] && break
  if [ "$elapsed" -ge "$TIMEOUT" ]; then
    fail "timeout esperando a:$pending"
  fi
  sleep 5
  elapsed=$((elapsed + 5))
done
echo "    todos healthy: $SERVICES"

echo "==> Job de inicialización de MinIO"
init_cid=$(compose ps -a -q minio-init)
init_code=$(docker inspect --format '{{.State.ExitCode}}' "$init_cid" 2>/dev/null)
[ "$init_code" = "0" ] || fail "minio-init terminó con código '$init_code' (bucket sin crear)"

echo "==> Pruebas con curl"
WEB="http://127.0.0.1:${WEB_PORT}"
API="http://127.0.0.1:${BACKEND_PORT}"

check_status() { # descripción url código-esperado
  code=$(curl -s -o /dev/null -w '%{http_code}' "$2")
  [ "$code" = "$3" ] || fail "$1: $2 devolvió $code y se esperaba $3"
  echo "    ok  $1 ($3)"
}

check_status "backend directo /health" "$API/health" 200
check_status "backoffice sirve la SPA" "$WEB/" 200
check_status "proxy NGINX -> backend /salud" "$WEB/salud" 200
check_status "ruta profunda de la SPA cae en index" "$WEB/planificacion/rutas" 200
check_status "asset inexistente da 404" "$WEB/assets/no-existe.png" 404

headers=$(curl -s -D - -o /dev/null "$WEB/")
for h in "Content-Security-Policy" "Strict-Transport-Security" "X-Frame-Options: DENY" "X-Content-Type-Options: nosniff"; do
  echo "$headers" | grep -qi "^$h" || fail "falta el header de seguridad: $h"
done
echo "    ok  headers de seguridad presentes"

# La CSP (script-src 'self') bloquea handlers inline: un `onload` en el index dejaría la página sin estilos.
curl -s "$WEB/" | grep -q 'onload=' && fail "el index.html trae un handler inline (onload) que la CSP bloquea"
echo "    ok  el index.html no trae handlers inline"

curl -s "$WEB/assets/no-existe.png" | grep -qi "planillero" || fail "la página 404 no es la personalizada"
echo "    ok  página 404 personalizada"

echo "==> Contenedores sin privilegios"
for svc in backend backoffice; do
  uid=$(compose exec -T "$svc" id -u | tr -d '\r')
  [ "$uid" != "0" ] || fail "$svc corre como root"
  echo "    ok  $svc corre con uid $uid"
done

echo "==> Cuando el backend se cae, NGINX responde con la página 502 propia"
compose stop backend >/dev/null || fail "no se pudo detener el backend"
check_status "backend caído -> 502" "$WEB/salud" 502
curl -s "$WEB/salud" | grep -qi "planillero" || fail "la página 502 no es la personalizada"
echo "    ok  página 502 personalizada"

echo "OK: el entorno integral pasó todas las verificaciones."
