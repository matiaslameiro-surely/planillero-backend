#!/bin/sh
# Valida la disponibilidad y correcto funcionamiento del entorno de producción desplegado.
#
# Uso:
#   ./scripts/verify-deployment.sh [URL_PUBLICA]
#
# Ejemplo:
#   ./scripts/verify-deployment.sh https://planillero.ferchamorro.cloud

set -e

URL="${1:-https://planillero.ferchamorro.cloud}"
# Quitar barra final si la tiene
URL="${URL%/}"

echo "============================================================"
echo " Verificación de Despliegue de Planillero"
echo " Destino: $URL"
echo "============================================================"
echo ""

EXIT_CODE=0

# 1. Carga de Backoffice (/)
echo "[1/4] Verificando acceso web al Backoffice ($URL/)..."
HTTP_CODE=$(curl -k -s -o /dev/null -w "%{http_code}" "$URL/" || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    echo "  -> OK: HTTP 200 recibido"
else
    echo "  -> ERROR: Se esperaba HTTP 200, recibido: $HTTP_CODE"
    EXIT_CODE=1
fi

# 2. Endpoint de salud de la API (/health)
echo ""
echo "[2/4] Verificando endpoint de salud ($URL/health)..."
HEALTH_RESP=$(curl -k -s "$URL/health" || echo "")
if echo "$HEALTH_RESP" | grep -qi "ok\|up\|healthy\|status"; then
    echo "  -> OK: API respondió correctamente:"
    echo "     $HEALTH_RESP"
else
    echo "  -> ERROR: Respuesta de salud inesperada o fallida:"
    echo "     $HEALTH_RESP"
    EXIT_CODE=1
fi

# 3. Cabeceras de seguridad (OWASP A05)
echo ""
echo "[3/4] Verificando cabeceras de seguridad en $URL/..."
HEADERS=$(curl -k -s -I "$URL/" || echo "")

check_header() {
    HEADER_NAME="$1"
    if echo "$HEADERS" | grep -qi "^$HEADER_NAME:"; then
        echo "  -> OK: Cabecera $HEADER_NAME presente"
    else
        echo "  -> ADVERTENCIA: Cabecera $HEADER_NAME no detectada en la respuesta"
    fi
}

check_header "Content-Security-Policy"
check_header "Strict-Transport-Security"
check_header "X-Frame-Options"
check_header "X-Content-Type-Options"

# 4. Prueba de autenticación con usuario de demo (admin.demo / Admin123!)
echo ""
echo "[4/4] Verificando autenticación con usuario demo (admin.demo)..."
LOGIN_PAYLOAD='{"username":"admin.demo","password":"Admin123!"}'
LOGIN_RESP=$(curl -k -s -X POST "$URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "$LOGIN_PAYLOAD" || echo "")

if echo "$LOGIN_RESP" | grep -qi "token\|accessToken\|refreshToken"; then
    echo "  -> OK: Inicio de sesión exitoso con credenciales de demo"
else
    echo "  -> ERROR al iniciar sesión con admin.demo. Respuesta:"
    echo "     $LOGIN_RESP"
    EXIT_CODE=1
fi

echo ""
echo "============================================================"
if [ $EXIT_CODE -eq 0 ]; then
    echo " ¡Todo OK! El entorno se encuentra operativo y validado."
else
    echo " Se encontraron problemas durante la verificación."
fi
echo "============================================================"

exit $EXIT_CODE
