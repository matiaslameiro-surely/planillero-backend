#!/bin/sh
# Genera par de claves RSA 2048 (PKCS#8 y X.509) para tokens JWT y un secreto HMAC aleatorio.
#
# Uso:
#   cd backend
#   ./scripts/generate-jwt-keys.sh

set -e

DIR="$(cd "$(dirname "$0")/.." && pwd)"
KEYS_DIR="$DIR/keys"

echo "=== Generador de Claves y Secretos de Producción ==="
echo ""

mkdir -p "$KEYS_DIR"

if [ -f "$KEYS_DIR/private.pem" ] && [ -f "$KEYS_DIR/public.pem" ]; then
    echo "Aviso: Ya existen claves en $KEYS_DIR."
    echo "Si deseas regenerarlas, borralas primero o ejecuta con FORCE=1."
    if [ "$FORCE" != "1" ]; then
        echo "Omitiendo generación de claves."
    fi
fi

if [ ! -f "$KEYS_DIR/private.pem" ] || [ "$FORCE" = "1" ]; then
    echo "Generando clave privada RSA 2048 (PKCS#8)..."
    openssl genpkey -algorithm RSA -out "$KEYS_DIR/private.pem" -pkeyopt rsa_keygen_bits:2048
    chmod 600 "$KEYS_DIR/private.pem"

    echo "Extrayendo clave pública (X.509)..."
    openssl rsa -pubout -in "$KEYS_DIR/private.pem" -out "$KEYS_DIR/public.pem"
    chmod 644 "$KEYS_DIR/public.pem"
    echo "Claves generadas con éxito en: $KEYS_DIR/"
fi

echo ""
echo "--- Secreto HMAC-SHA256 sugerido ---"
RANDOM_HMAC=$(openssl rand -hex 32 2>/dev/null || cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 64 | head -n 1)
echo "Copiá este valor en HMAC_SECRET dentro de tu .env.production:"
echo "$RANDOM_HMAC"
echo ""
echo "Listo. Recordá no compartir ni versionar en git el archivo .env.production ni la carpeta keys/."
