// Atheryon Mortgages — Azure Container App (Bicep)
// Declarative IaC for ca-mortgages-dev
// References existing shared infrastructure by name

@description('Environment suffix (dev, staging, prod)')
param environment string = 'dev'

@description('Container image tag')
param imageTag string = 'latest'

@description('Azure region')
param location string = resourceGroup().location

@description('PostgreSQL connection string (ignored in dev — uses H2)')
@secure()
param dbUrl string = ''

@description('PostgreSQL username')
@secure()
param dbUsername string = ''

@description('PostgreSQL password')
@secure()
param dbPassword string = ''

// ── Naming ──────────────────────────────────────────────────────────────────────
var containerAppName = 'ca-mortgages-${environment}'
var registryName = 'crlabsdev'
var registryServer = '${registryName}.azurecr.io'
var imageName = 'atheryon-mortgages'
var containerEnvName = 'cdm-containerenv-${environment}'

// ── Reference existing Container Apps Environment ───────────────────────────────
resource containerEnv 'Microsoft.App/managedEnvironments@2023-05-01' existing = {
  name: containerEnvName
}

// ── Container App ───────────────────────────────────────────────────────────────
resource containerApp 'Microsoft.App/containerApps@2023-05-01' = {
  name: containerAppName
  location: location
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    managedEnvironmentId: containerEnv.id
    configuration: {
      activeRevisionsMode: 'Single'
      secrets: [
        { name: 'db-url', value: dbUrl }
        { name: 'db-username', value: dbUsername }
        { name: 'db-password', value: dbPassword }
      ]
      ingress: {
        external: false // internal only — labs-platform proxies to this
        targetPort: 8080
        transport: 'auto'
        allowInsecure: false
      }
      registries: [
        {
          server: registryServer
          identity: 'system'
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'atheryon-mortgages'
          image: '${registryServer}/${imageName}:${imageTag}'
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          env: [
            { name: 'SPRING_PROFILES_ACTIVE', value: environment }
            { name: 'JAVA_OPTS', value: '-Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseContainerSupport' }
            // Database — only used when profile != dev (dev uses H2 in-memory)
            { name: 'DB_URL', secretRef: 'db-url' }
            { name: 'DB_USERNAME', secretRef: 'db-username' }
            { name: 'DB_PASSWORD', secretRef: 'db-password' }
          ]
          probes: [
            {
              type: 'Startup'
              httpGet: {
                path: '/actuator/health'
                port: 8080
              }
              initialDelaySeconds: 10
              periodSeconds: 5
              failureThreshold: 30 // 10s + 30*5s = 160s max startup
            }
            {
              type: 'Liveness'
              httpGet: {
                path: '/actuator/health/liveness'
                port: 8080
              }
              periodSeconds: 15
              failureThreshold: 3
            }
            {
              type: 'Readiness'
              httpGet: {
                path: '/actuator/health/readiness'
                port: 8080
              }
              periodSeconds: 10
              failureThreshold: 3
            }
          ]
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 3
      }
    }
  }
}

// ── ACR Pull Role Assignment ────────────────────────────────────────────────────
// AcrPull built-in role: 7f951dda-4ed3-4680-a7ca-43fe172d538d
resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(containerApp.id, 'acrpull')
  scope: resourceGroup()
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')
    principalId: containerApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// ── Outputs ─────────────────────────────────────────────────────────────────────
output containerAppName string = containerApp.name
output fqdn string = containerApp.properties.configuration.ingress.fqdn
output principalId string = containerApp.identity.principalId
