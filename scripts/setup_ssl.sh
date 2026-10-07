#!/usr/bin/env bash
###############################################################################
# Ktab Backend - Automated SSL Setup Script (Let's Encrypt / Certbot)
# Usage: sudo bash scripts/setup_ssl.sh <your-domain.com> <your-email@example.com>
#
# Environment (optional):
#   CLIENT_MAX_BODY_SIZE   largest request Nginx accepts (default 100M). The app accepts PDFs up to 200 MB for
#                          extraction (KTAB_EXTRACTION_MAX_UPLOAD_BYTES), so use e.g. 220M to allow them.
#
# Safe to run again: a certificate that is still valid for more than 30 days is kept (no downtime), and only the
# Nginx configuration is rewritten. NOTE: this script writes nginx/conf.d/ktab.conf completely, so every change you
# want in the HTTPS configuration belongs in this script, not only in that file.
###############################################################################

set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: sudo bash scripts/setup_ssl.sh <DOMAIN> <EMAIL>"
  echo "Example: sudo bash scripts/setup_ssl.sh api.ktab.app admin@ktab.app"
  exit 1
fi

DOMAIN="$1"
EMAIL="$2"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_MAX_BODY_SIZE="${CLIENT_MAX_BODY_SIZE:-100M}"
CERT="/etc/letsencrypt/live/${DOMAIN}/fullchain.pem"

echo "============================================================"
echo "  🔒 Setting up Let's Encrypt SSL for: $DOMAIN"
echo "  Upload limit in Nginx: $CLIENT_MAX_BODY_SIZE"
echo "============================================================"

# Ensure challenge and cert directories exist
mkdir -p /var/www/certbot
mkdir -p /etc/letsencrypt

# Install Certbot if not already installed
if ! command -v certbot >/dev/null 2>&1; then
  echo "[*] Installing Certbot..."
  apt-get update -y
  apt-get install -y certbot
fi

# Request a certificate only when there is none, or it expires within 30 days
if [ -f "$CERT" ] && openssl x509 -checkend 2592000 -noout -in "$CERT" >/dev/null 2>&1; then
  echo "[*] A valid certificate for $DOMAIN already exists (more than 30 days left): keeping it."
else
  # Temporarily stop ktab-nginx to free port 80 for the standalone ACME challenge
  echo "[*] Temporarily stopping ktab-nginx to free port 80..."
  docker stop ktab-nginx 2>/dev/null || true

  echo "[*] Requesting SSL certificate from Let's Encrypt for $DOMAIN..."
  certbot certonly --standalone \
    -d "$DOMAIN" \
    --email "$EMAIL" \
    --agree-tos \
    --no-eff-email \
    --non-interactive
fi

# Keep a copy of the configuration that is being replaced (outside conf.d so Nginx does not load it)
CONF="${ROOT_DIR}/nginx/conf.d/ktab.conf"
if [ -f "$CONF" ]; then
  cp -p "$CONF" "${ROOT_DIR}/nginx/ktab.conf.bak-$(date +%Y%m%d-%H%M%S)"
fi

# Write updated Nginx configuration with SSL
echo "[*] Updating Nginx configuration for HTTPS..."
cat <<EOF > "$CONF"
# Redirect HTTP to HTTPS
server {
    listen 80;
    listen [::]:80;
    server_name ${DOMAIN};

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://\$host\$request_uri;
    }
}

# HTTPS Server
server {
    listen 443 ssl;
    listen [::]:443 ssl;
    http2 on;
    server_name ${DOMAIN};

    ssl_certificate /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;

    client_max_body_size ${CLIENT_MAX_BODY_SIZE};

    # Re-resolve ktab-app's address via Docker's embedded DNS instead of caching it for the
    # life of the worker process, so a redeployed/recreated ktab-app container (new IP) is
    # picked up without requiring an Nginx restart.
    resolver 127.0.0.11 valid=10s;

    # Server-sent events: the conclusion text is streamed, so Nginx must not hold it back in a buffer
    location = /api/v1/conclusion/stream {
        set \$upstream_app ktab-app:8080;
        proxy_pass http://\$upstream_app;
        proxy_http_version 1.1;

        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Host \$host;
        proxy_set_header X-Forwarded-Port \$server_port;

        proxy_buffering off;
        proxy_cache off;
        proxy_connect_timeout 90s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
    }

    location / {
        set \$upstream_app ktab-app:8080;
        proxy_pass http://\$upstream_app;
        proxy_http_version 1.1;

        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Host \$host;
        proxy_set_header X-Forwarded-Port \$server_port;

        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";

        proxy_connect_timeout 90s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;

        proxy_buffering on;
        proxy_buffer_size 128k;
        proxy_buffers 4 256k;
        proxy_busy_buffers_size 256k;
    }

    location /health {
        access_log off;
        return 200 "nginx-ssl-ok\n";
        add_header Content-Type text/plain;
    }
}
EOF

# Setup automatic renewal hooks
mkdir -p /etc/letsencrypt/renewal-hooks/pre /etc/letsencrypt/renewal-hooks/post
cat << 'HOOK' > /etc/letsencrypt/renewal-hooks/pre/stop-nginx.sh
#!/bin/bash
docker stop ktab-nginx 2>/dev/null || true
HOOK
chmod +x /etc/letsencrypt/renewal-hooks/pre/stop-nginx.sh

cat << 'HOOK' > /etc/letsencrypt/renewal-hooks/post/start-nginx.sh
#!/bin/bash
docker start ktab-nginx 2>/dev/null || true
HOOK
chmod +x /etc/letsencrypt/renewal-hooks/post/start-nginx.sh

# Recreate and start Nginx container with SSL mounts
echo "[*] Starting Nginx container with SSL..."
docker compose -f "${ROOT_DIR}/docker-compose.yml" --env-file "${ROOT_DIR}/.env.production" up -d --force-recreate ktab-nginx

# Check the configuration the container actually loaded
sleep 3
if docker exec ktab-nginx nginx -t >/dev/null 2>&1; then
  echo "[*] Nginx configuration test passed."
else
  echo "[-] ERROR: Nginx rejected the configuration. Output:" >&2
  docker exec ktab-nginx nginx -t >&2 || docker logs --tail 30 ktab-nginx >&2 || true
  echo "    The previous configuration was saved as nginx/ktab.conf.bak-*; copy it back to nginx/conf.d/ktab.conf to undo." >&2
  exit 1
fi

echo "============================================================"
echo "  ✅ SSL configured successfully for https://${DOMAIN}"
echo "  🔄 Automatic renewal hooks installed in /etc/letsencrypt/renewal-hooks/"
echo "  Test:  curl -I https://${DOMAIN}/actuator/health"
echo "============================================================"
