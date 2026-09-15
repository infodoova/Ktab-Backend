#!/usr/bin/env bash
###############################################################################
# Contabo VPS Initial Server Setup Script for Ktab Backend
# Target OS: Ubuntu 22.04 / 24.04 LTS
# Run as root or with sudo: sudo bash scripts/vps_setup.sh
###############################################################################

set -euo pipefail

# Ensure script is run as root
if [ "$EUID" -ne 0 ]; then
  echo "[-] Please run as root (use: sudo bash scripts/vps_setup.sh)"
  exit 1
fi

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
  wget

# 3. Configure 4GB Swap Space (Prevents OOM during build & runtime)
echo "[3/6] Checking and configuring Swap space..."
CURRENT_SWAP=$(free -m | awk '/^Swap:/ {print $2}')
if [ "$CURRENT_SWAP" -lt 2048 ]; then
  echo "    Configuring 4GB swap file..."
  if [ -f /swapfile ]; then
    swapoff /swapfile 2>/dev/null || true
    rm -f /swapfile
  fi
  fallocate -l 4G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=4096
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

# Configure Docker daemon log rotation to prevent disk exhaustion
mkdir -p /etc/docker
cat <<EOF > /etc/docker/daemon.json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "30m",
    "max-file": "5"
  }
}
EOF

systemctl daemon-reload
systemctl restart docker
systemctl enable docker

# 5. Configure UFW Firewall
echo "[5/6] Configuring UFW Firewall..."
ufw --force reset >/dev/null 2>&1 || true
ufw default deny incoming
ufw default allow outgoing
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
echo "2. Create .env.production file with your real secrets"
echo "3. Run: bash scripts/deploy.sh"
echo "============================================================"
