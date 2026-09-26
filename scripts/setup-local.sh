#!/usr/bin/env bash
# One-time setup of TableTap on the restaurant's computer (Mac or Linux with Docker).
# Creates .env with strong random secrets, starts everything, and prints the address for phones.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  echo ".env already exists — keeping your existing secrets."
else
  read -rp "Admin email for this restaurant [admin@tabletap.local]: " ADMIN_EMAIL
  ADMIN_EMAIL=${ADMIN_EMAIL:-admin@tabletap.local}
  ADMIN_PASSWORD=$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-16)1a
  umask 077
  cat > .env <<ENV
DB_USER=tabletap
DB_PASSWORD=$(openssl rand -hex 24)
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
ADMIN_EMAIL=$ADMIN_EMAIL
ADMIN_PASSWORD=$ADMIN_PASSWORD
DEMO_DATA=false
ENV
  echo
  echo "Admin login:  $ADMIN_EMAIL"
  echo "Password:     $ADMIN_PASSWORD   <- write this down now, it is only shown once"
fi

mkdir -p backups
docker compose up -d --build

IP=$( (ipconfig getifaddr en0 || ipconfig getifaddr en1 || hostname -I | awk '{print $1}') 2>/dev/null || true)
echo
echo "TableTap is starting. On phones/tablets connected to the restaurant Wi-Fi, open:"
echo "   http://${IP:-<this-computer-IP>}:${APP_PORT:-8080}"
echo "Tip: give this computer a fixed IP address in your router so the address never changes."
