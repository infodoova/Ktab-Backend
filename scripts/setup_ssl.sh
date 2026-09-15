#!/usr/bin/env bash
###############################################################################
# Ktab Backend - Automated SSL Setup Script (Let's Encrypt / Certbot)
# Usage: sudo bash scripts/setup_ssl.sh <your-domain.com> <your-email@example.com>
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

echo "============================================================"
echo "  🔒 Setting up Let's Encrypt SSL for: $DOMAIN"
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

# Temporarily stop ktab-nginx to free port 80 for standalone ACME challenge
echo "[*] Temporarily stopping ktab-nginx to free port 80..."
docker stop ktab-nginx 2>/dev/null || true

# Request certificate using standalone mode
echo "[*] Requesting SSL certificate from Let's Encrypt for $DOMAIN..."
certbot certonly --standalone \
  -d "$DOMAIN" \
  --email "$EMAIL" \
  --agree-tos \
  --no-eff-email \
  --non-interactive

# Write updated Nginx configuration with SSL
echo "[*] Updating Nginx configuration for HTTPS..."
cat <<EOF > "${ROOT_DIR}/nginx/conf.d/ktab.conf"
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

    client_max_body_size 100M;

    location / {
        proxy_pass http://ktab-app:8080;
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

echo "============================================================"
echo "  ✅ SSL configured successfully for https://${DOMAIN}"
echo "  🔄 Automatic renewal hooks installed in /etc/letsencrypt/renewal-hooks/"
echo "============================================================"
