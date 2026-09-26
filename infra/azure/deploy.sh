#!/usr/bin/env bash
# Deploys TableTap to Azure (Container Apps + PostgreSQL) from this folder.
#
#   az login
#   ADMIN_EMAIL=you@example.com ./infra/azure/deploy.sh
#
# Optional: RG (resource group), LOCATION, APP_NAME.
# Secrets are generated on the first run and kept in infra/azure/.deploy.parameters.json
# (git-ignored, readable only by you). Re-running the script redeploys with the same secrets.
set -euo pipefail
cd "$(dirname "$0")/../.."

RG=${RG:-rg-tabletap}
LOCATION=${LOCATION:-australiaeast}
APP_NAME=${APP_NAME:-tabletap}
PARAMS=infra/azure/.deploy.parameters.json
TEMPLATE=infra/azure/main.bicep

az account show >/dev/null 2>&1 || { echo "Not logged in — run: az login"; exit 1; }
echo "Subscription: $(az account show --query name -o tsv)"

if [[ ! -f $PARAMS ]]; then
  : "${ADMIN_EMAIL:?First run: set ADMIN_EMAIL=you@example.com}"
  ADMIN_PASSWORD="$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-16)A1"
  umask 077
  cat > "$PARAMS" <<JSON
{
  "\$schema": "https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#",
  "contentVersion": "1.0.0.0",
  "parameters": {
    "appName": { "value": "$APP_NAME" },
    "adminEmail": { "value": "$ADMIN_EMAIL" },
    "adminPassword": { "value": "$ADMIN_PASSWORD" },
    "dbPassword": { "value": "$(openssl rand -hex 24)" },
    "jwtSecret": { "value": "$(openssl rand -base64 64 | tr -d '\n')" }
  }
}
JSON
  echo
  echo "Admin login:    $ADMIN_EMAIL"
  echo "Admin password: $ADMIN_PASSWORD   <- save this now (also stored in $PARAMS)"
  echo
fi

echo "==> Resource group $RG ($LOCATION)"
az group create -n "$RG" -l "$LOCATION" -o none

output() { az deployment group show -g "$RG" -n tabletap --query "properties.outputs.$1.value" -o tsv; }

# 1) Infrastructure. The first time there is no image yet, so the app starts a placeholder.
EXISTING_TAG=$(az containerapp show -g "$RG" -n "$APP_NAME" --query "properties.template.containers[0].image" -o tsv 2>/dev/null | grep -o ':[^:]*$' | tr -d ':' || true)
[[ $EXISTING_TAG == latest ]] && EXISTING_TAG=""
echo "==> Infrastructure (a first deploy takes ~10 minutes, mostly PostgreSQL)"
az deployment group create -g "$RG" -n tabletap -f "$TEMPLATE" -p "@$PARAMS" -p location="$LOCATION" imageTag="$EXISTING_TAG" -o none

ACR=$(output acrName)

# 2) Build the image inside Azure (no local Docker needed) and roll it out.
TAG=$(date +%Y%m%d%H%M%S)
echo "==> Building image tabletap:$TAG in $ACR"
az acr build -r "$ACR" -t "tabletap:$TAG" -t tabletap:latest . -o none

echo "==> Deploying image"
az deployment group create -g "$RG" -n tabletap -f "$TEMPLATE" -p "@$PARAMS" -p location="$LOCATION" imageTag="$TAG" -o none

URL=$(output appUrl)
echo "==> Waiting for $URL to become healthy"
for _ in $(seq 1 60); do
  if curl -fs "$URL/actuator/health" | grep -q UP; then
    echo
    echo "TableTap is live: $URL"
    echo "Redeploy after code changes: re-run this script (or push to main with the GitHub Actions workflow)."
    exit 0
  fi
  sleep 5
done
echo "App did not report healthy yet. Logs: az containerapp logs show -g $RG -n $APP_NAME --follow"
exit 1
