#!/usr/bin/env bash
# One-time: lets GitHub Actions deploy to Azure without storing any password (OpenID Connect).
#
#   GITHUB_REPO=your-user/tabletap ./infra/azure/setup-github.sh
#
# Run after deploy.sh has created the resources. Prints the values to add in
# GitHub -> Settings -> Secrets and variables -> Actions.
set -euo pipefail
: "${GITHUB_REPO:?set GITHUB_REPO=owner/repo}"
RG=${RG:-rg-tabletap}
APP_NAME=${APP_NAME:-tabletap}

SUB=$(az account show --query id -o tsv)
TENANT=$(az account show --query tenantId -o tsv)
ACR_ID=$(az acr list -g "$RG" --query "[0].id" -o tsv)
ACR=$(az acr list -g "$RG" --query "[0].name" -o tsv)
RG_ID=$(az group show -n "$RG" --query id -o tsv)

APP_ID=$(az ad app create --display-name "tabletap-github-deploy" --query appId -o tsv)
az ad sp create --id "$APP_ID" -o none 2>/dev/null || true
SP_ID=$(az ad sp show --id "$APP_ID" --query id -o tsv)

az ad app federated-credential create --id "$APP_ID" --parameters "{
  \"name\": \"github-main\",
  \"issuer\": \"https://token.actions.githubusercontent.com\",
  \"subject\": \"repo:${GITHUB_REPO}:ref:refs/heads/main\",
  \"audiences\": [\"api://AzureADTokenExchange\"]
}" -o none

# Least privilege: manage the app in this resource group + push images to this registry.
az role assignment create --assignee-object-id "$SP_ID" --assignee-principal-type ServicePrincipal --role Contributor --scope "$RG_ID" -o none
az role assignment create --assignee-object-id "$SP_ID" --assignee-principal-type ServicePrincipal --role AcrPush --scope "$ACR_ID" -o none

cat <<OUT

Add these in GitHub (Settings -> Secrets and variables -> Actions):
  Secrets:   AZURE_CLIENT_ID=$APP_ID
             AZURE_TENANT_ID=$TENANT
             AZURE_SUBSCRIPTION_ID=$SUB
  Variables: AZURE_RESOURCE_GROUP=$RG
             AZURE_ACR_NAME=$ACR
             AZURE_CONTAINER_APP=$APP_NAME
OUT
