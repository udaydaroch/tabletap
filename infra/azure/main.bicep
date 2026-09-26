// TableTap on Azure: Container Apps (the Spring Boot + React image) + PostgreSQL Flexible Server.
// Deployed by ./deploy.sh — see README "Deploy to Azure".

targetScope = 'resourceGroup'

@description('Short lowercase name used to prefix every resource.')
@minLength(3)
@maxLength(12)
param appName string = 'tabletap'

param location string = resourceGroup().location

@description('Image tag in the registry to run. Leave empty on the very first deploy (before any image exists) to start a placeholder.')
param imageTag string = ''

@description('Email of the platform admin account created on first start.')
param adminEmail string

@secure()
@description('Password for the platform admin account.')
param adminPassword string

@secure()
@description('PostgreSQL administrator password.')
param dbPassword string

@secure()
@description('Signing key for login tokens. At least 48 random characters (the app refuses to start otherwise).')
param jwtSecret string

@description('Monthly price of the floor-plan add-on, shown to owners before they upgrade.')
param floorPlanFee string = '10.00'

@description('PostgreSQL size. Standard_B1ms (Burstable) is the cheapest; move to General Purpose for busy production.')
param postgresSku string = 'Standard_B1ms'
param postgresTier string = 'Burstable'

@description('Keep at 1 for now: live updates and login rate limits are held in memory, so all users must hit the same replica. Raise only after moving those to Redis.')
@minValue(1)
@maxValue(10)
param maxReplicas int = 1

var suffix = uniqueString(resourceGroup().id)
// Registry names must be 5-50 letters/digits: appName (3-12) + 'acr' + 13-char suffix is always 19-28.
var acrName = replace('${appName}acr${suffix}', '-', '')
var dbAdminUser = 'tabletapadmin'
var hasImage = !empty(imageTag)
var image = hasImage ? '${acr.properties.loginServer}/tabletap:${imageTag}' : 'mcr.microsoft.com/k8se/quickstart:latest'

// ---------- logging ----------
resource logs 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: '${appName}-logs-${suffix}'
  location: location
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
  }
}

// ---------- container registry (pulled with a managed identity, no passwords) ----------
resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  #disable-next-line BCP334 // length is guaranteed by the comment on acrName
  name: acrName
  location: location
  sku: { name: 'Basic' }
  properties: {
    adminUserEnabled: false
  }
}

resource appIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: '${appName}-id-${suffix}'
  location: location
}

var acrPullRoleId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')

resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, appIdentity.id, acrPullRoleId)
  scope: acr
  properties: {
    roleDefinitionId: acrPullRoleId
    principalId: appIdentity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

// ---------- database ----------
resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: '${appName}-pg-${suffix}'
  location: location
  sku: {
    name: postgresSku
    tier: postgresTier
  }
  properties: {
    version: '16'
    administratorLogin: dbAdminUser
    administratorLoginPassword: dbPassword
    storage: { storageSizeGB: 32 }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: { mode: 'Disabled' }
    network: { publicNetworkAccess: 'Enabled' }
  }
}

// 0.0.0.0 = only traffic from inside Azure (the Container App). TLS is required by default.
resource allowAzure 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: postgres
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource database 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgres
  name: 'tabletap'
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

// ---------- container app ----------
resource containerEnv 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${appName}-env-${suffix}'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

resource app 'Microsoft.App/containerApps@2024-03-01' = {
  name: appName
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: { '${appIdentity.id}': {} }
  }
  properties: {
    managedEnvironmentId: containerEnv.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: hasImage ? 8080 : 80 // placeholder image listens on 80
        transport: 'auto'
        allowInsecure: false // HTTPS only (Azure provides the certificate)
      }
      registries: [
        {
          server: acr.properties.loginServer
          identity: appIdentity.id
        }
      ]
      secrets: [
        { name: 'db-password', value: dbPassword }
        { name: 'jwt-secret', value: jwtSecret }
        { name: 'admin-password', value: adminPassword }
      ]
    }
    template: {
      containers: [
        {
          name: 'tabletap'
          image: image
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            { name: 'DB_URL', value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/${database.name}?sslmode=require' }
            { name: 'DB_USER', value: dbAdminUser }
            { name: 'DB_PASSWORD', secretRef: 'db-password' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            { name: 'ADMIN_EMAIL', value: adminEmail }
            { name: 'ADMIN_PASSWORD', secretRef: 'admin-password' }
            { name: 'DEMO_DATA', value: 'false' }
            { name: 'BILLING_FLOOR_PLAN_FEE', value: floorPlanFee }
            { name: 'JAVA_OPTS', value: '-XX:MaxRAMPercentage=75' }
          ]
          probes: hasImage ? [
            {
              type: 'Startup'
              httpGet: { path: '/actuator/health/liveness', port: 8080 }
              periodSeconds: 5
              failureThreshold: 36 // allow up to 3 minutes for first start
            }
            {
              type: 'Liveness'
              httpGet: { path: '/actuator/health/liveness', port: 8080 }
              periodSeconds: 30
            }
            {
              type: 'Readiness'
              httpGet: { path: '/actuator/health/readiness', port: 8080 }
              periodSeconds: 10
            }
          ] : []
        }
      ]
      scale: {
        minReplicas: 1 // always on: restaurants can't wait for a cold start
        maxReplicas: maxReplicas
      }
    }
  }
  dependsOn: [
    acrPull
    allowAzure
  ]
}

output appUrl string = 'https://${app.properties.configuration.ingress.fqdn}'
output containerAppName string = app.name
output acrName string = acr.name
output acrLoginServer string = acr.properties.loginServer
output postgresHost string = postgres.properties.fullyQualifiedDomainName
