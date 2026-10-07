#!/usr/bin/env bash
###############################################################################
# Contabo VPS Initial Server Setup Script for Ktab Backend
# Target OS: Ubuntu 22.04 / 24.04 LTS
# Run as root or with sudo: sudo bash scripts/vps_setup.sh [--force]
#
# For a NEW server only. On a server that already runs Ktab this script refuses to run (it restarts Docker, which
# restarts every container); use --force if you really mean it. To update a running server use scripts/deploy.sh.
#
# Environment (optional):
#   SWAP_GB   size of the swap file when the server has less than 2 GB of swap (default 4)
###############################################################################

set -euo pipefail

FORCE=0
[ "${1:-}" = "--force" ] && FORCE=1

# Ensure script is run as root
if [ "$EUID" -ne 0 ]; then
  echo "[-] Please run as root (use: sudo bash scripts/vps_setup.sh)"
  exit 1
fi

# Do not touch a server that is already hosting Ktab
if command -v docker >/dev/null 2>&1 && docker ps -a --format '{{.Names}}' 2>/dev/null | grep -q '^ktab-'; then
  if [ "$FORCE" != "1" ]; then
    echo "[-] This server already has Ktab containers. Running the setup again would restart Docker (and with it every"
    echo "    container) and could change the firewall. To update the application run:  bash scripts/deploy.sh"
    echo "    Run this script with --force only if you know you need to."
    exit 1
  fi
  echo "[!] --force given: continuing on a server that already hosts Ktab."
fi

SWAP_GB="${SWAP_GB:-4}"

echo "============================================================"
echo "  🚀 Starting Contabo VPS Setup for Ktab Backend"
echo "============================================================"

# 1. Update and Upgrade System Packages
echo "[1/6] Updating system packages..."
apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get upgrade -y

# 2. Install Essential Tools
echo "[2/6] Installing essential utilities..."
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  ca-certificates \
  curl \
  gnupg \
  lsb-release \
  git \
  ufw \
  fail2ban \
  htop \
  unzip \
  wget \
  openssl

# 3. Configure Swap Space (a safety net against out-of-memory kills during image builds and PDF rendering)
echo "[3/6] Checking and configuring Swap space..."
CURRENT_SWAP=$(free -m | awk '/^Swap:/ {print $2}')
if [ "$CURRENT_SWAP" -lt 2048 ]; then
  echo "    Configuring ${SWAP_GB}GB swap file..."
  if [ -f /swapfile ]; then
    swapoff /swapfile 2>/dev/null || true
    rm -f /swapfile
  fi
  fallocate -l "${SWAP_GB}G" /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=$((SWAP_GB * 1024))
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  if ! grep -q '/swapfile' /etc/fstab; then
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
  fi
  # Optimize swappiness for servers
  sysctl vm.swappiness=20
  echo 'vm.swappiness=20' > /etc/sysctl.d/99-swappiness.conf
  echo "    Swap configured successfully."
else
  echo "    Adequate swap already detected: ${CURRENT_SWAP}MB."
fi

# 4. Install Official Docker Engine & Docker Compose Plugin
echo "[4/6] Installing official Docker & Docker Compose..."
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor --yes -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg

UBUNTU_CODENAME=$(lsb_release -cs)
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  ${UBUNTU_CODENAME} stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  docker-ce \
  docker-ce-cli \
  containerd.io \
  docker-buildx-plugin \
  docker-compose-plugin

# Configure Docker daemon log rotation to prevent disk exhaustion.
# The daemon is restarted only when this file actually changes, because a restart restarts every container.
mkdir -p /etc/docker
DAEMON_JSON="$(mktemp)"
cat <<EOF > "$DAEMON_JSON"
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "30m",
    "max-file": "5"
  }
}
EOF
if ! cmp -s "$DAEMON_JSON" /etc/docker/daemon.json 2>/dev/null; then
  cp "$DAEMON_JSON" /etc/docker/daemon.json
  systemctl daemon-reload
  systemctl restart docker
else
  echo "    Docker log rotation already configured."
fi
rm -f "$DAEMON_JSON"
systemctl enable docker

# 5. Configure UFW Firewall
echo "[5/6] Configuring UFW Firewall..."
if ufw status | grep -q "Status: active"; then
  # Never wipe the rules of a firewall that is already on: only make sure the three ports are open.
  echo "    UFW is already active: keeping its rules and making sure SSH, HTTP and HTTPS are allowed."
else
  ufw --force reset >/dev/null 2>&1 || true
  ufw default deny incoming
  ufw default allow outgoing
fi
ufw allow 22/tcp comment 'SSH'
ufw allow 80/tcp comment 'HTTP'
ufw allow 443/tcp comment 'HTTPS'
ufw --force enable

# 6. Configure & Enable Fail2Ban for SSH Brute Force Protection
echo "[6/6] Configuring Fail2Ban..."
systemctl restart fail2ban
systemctl enable fail2ban

echo "============================================================"
echo "  ✅ Contabo VPS Setup Completed Successfully!"
echo "============================================================"
echo "Next steps:"
echo "1. Clone your Ktab repository or cd into project directory"
echo "2. Create .env.production with your real secrets (see Step 4 of docs/contabo_deployment_guide.md)"
echo "   On a server that already has one: merge the new variables with  bash scripts/merge_env.sh"
echo "3. Run: bash scripts/deploy.sh"
echo "4. Then: sudo bash scripts/setup_ssl.sh api.ktab.app <your-email>"
echo
echo "Heads up: ports that Docker publishes (for example pgAdmin on 5050 in docker-compose.yml) are reachable from"
echo "the internet even though UFW does not list them. Remove or bind them to 127.0.0.1 (see the Hardening checklist)."
echo "============================================================"
