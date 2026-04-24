#!/bin/sh
# Generate a self-signed cert on first run and serve GMP-over-TLS.
# Backend: /run/gvmd/gvmd.sock (plain XML).
# Frontend: 0.0.0.0:9390 (TLS).
set -eu

CERT_DIR=/etc/gmp-bridge
mkdir -p "$CERT_DIR"
if [ ! -s "$CERT_DIR/server.pem" ]; then
  echo "[gmp-bridge] generating self-signed cert..."
  openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
    -subj '/CN=gmp-bridge/O=local-dev' \
    -keyout "$CERT_DIR/server.key" -out "$CERT_DIR/server.crt" >/dev/null 2>&1
  cat "$CERT_DIR/server.key" "$CERT_DIR/server.crt" > "$CERT_DIR/server.pem"
  chmod 600 "$CERT_DIR/server.pem"
fi

echo "[gmp-bridge] listening TLS on :9390 -> /run/gvmd/gvmd.sock"
exec socat \
  OPENSSL-LISTEN:9390,reuseaddr,fork,cert="$CERT_DIR/server.pem",verify=0 \
  UNIX-CONNECT:/run/gvmd/gvmd.sock
