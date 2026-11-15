locals {
  # Postgres and Redis names are global across Azure: a random suffix avoids collisions.
  suffix   = random_string.suffix.result
  registry = "ghcr.io/${var.github_owner}"

  # Container Apps gives each external app a fixed address in the environment's domain.
  # Computing it here avoids a dependency loop between the API and the web app.
  public_url = "https://vigie-web.${azurerm_container_app_environment.main.default_domain}"

  tags = {
    project = "vigie"
  }
}

resource "random_string" "suffix" {
  length  = 6
  upper   = false
  special = false
}

resource "random_password" "database" {
  length  = 32
  special = false
}

# 48 random bytes, Base64-encoded: the HMAC key that signs session tokens.
resource "random_bytes" "jwt_secret" {
  length = 48
}

resource "azurerm_resource_group" "main" {
  name     = var.resource_group_name
  location = var.location
  tags     = local.tags
}

resource "azurerm_log_analytics_workspace" "main" {
  name                = "log-vigie-${local.suffix}"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = local.tags
}

resource "azurerm_container_app_environment" "main" {
  name                       = "cae-vigie"
  resource_group_name        = azurerm_resource_group.main.name
  location                   = azurerm_resource_group.main.location
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  tags                       = local.tags
}

# ---------------------------------------------------------------- PostgreSQL

resource "azurerm_postgresql_flexible_server" "main" {
  name                          = "psql-vigie-${local.suffix}"
  resource_group_name           = azurerm_resource_group.main.name
  location                      = azurerm_resource_group.main.location
  version                       = "16"
  sku_name                      = "B_Standard_B1ms"
  storage_mb                    = 32768
  backup_retention_days         = 7
  administrator_login           = "vigieadmin"
  administrator_password        = random_password.database.result
  public_network_access_enabled = true
  tags                          = local.tags

  lifecycle {
    # Azure picks an availability zone; do not try to move the server afterwards.
    ignore_changes = [zone]
  }
}

resource "azurerm_postgresql_flexible_server_database" "vigie" {
  name      = "vigie"
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# 0.0.0.0 is Azure's convention for "services hosted in Azure", which includes Container Apps.
resource "azurerm_postgresql_flexible_server_firewall_rule" "azure_services" {
  name             = "allow-azure-services"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "reporting" {
  count            = var.reporting_ip == "" ? 0 : 1
  name             = "allow-reporting"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = var.reporting_ip
  end_ip_address   = var.reporting_ip
}

# ---------------------------------------------------------------- Redis

resource "azurerm_redis_cache" "main" {
  name                 = "redis-vigie-${local.suffix}"
  resource_group_name  = azurerm_resource_group.main.name
  location             = azurerm_resource_group.main.location
  capacity             = 0
  family               = "C"
  sku_name             = "Basic"
  minimum_tls_version  = "1.2"
  non_ssl_port_enabled = false
  tags                 = local.tags
}

# ---------------------------------------------------------------- Applications

resource "azurerm_container_app" "api" {
  name                         = "vigie-api"
  resource_group_name          = azurerm_resource_group.main.name
  container_app_environment_id = azurerm_container_app_environment.main.id
  revision_mode                = "Single"
  tags                         = local.tags

  secret {
    name  = "database-password"
    value = random_password.database.result
  }

  secret {
    name  = "redis-password"
    value = azurerm_redis_cache.main.primary_access_key
  }

  secret {
    name  = "jwt-secret"
    value = random_bytes.jwt_secret.base64
  }

  secret {
    name  = "admin-password"
    value = var.admin_password == "" ? "unused" : var.admin_password
  }

  secret {
    name  = "mail-password"
    value = var.mail_password == "" ? "unused" : var.mail_password
  }

  # Internal only: the API is reachable from the web app, never from the internet.
  ingress {
    external_enabled = false
    target_port      = 8080
    transport        = "http"
    # Plain HTTP inside the environment; the public HTTPS endpoint is the web app.
    allow_insecure_connections = true

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    # Exactly one replica: it runs the scheduled scans and keeps the sign-in
    # throttle in memory, both of which assume a single instance.
    min_replicas = 1
    max_replicas = 1

    container {
      name   = "api"
      image  = "${local.registry}/vigie-api:${var.image_tag}"
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "SPRING_DATASOURCE_URL"
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/${azurerm_postgresql_flexible_server_database.vigie.name}?sslmode=require"
      }
      env {
        name  = "SPRING_DATASOURCE_USERNAME"
        value = azurerm_postgresql_flexible_server.main.administrator_login
      }
      env {
        name        = "SPRING_DATASOURCE_PASSWORD"
        secret_name = "database-password"
      }
      env {
        name  = "REDIS_HOST"
        value = azurerm_redis_cache.main.hostname
      }
      env {
        name  = "REDIS_PORT"
        value = tostring(azurerm_redis_cache.main.ssl_port)
      }
      env {
        name        = "REDIS_PASSWORD"
        secret_name = "redis-password"
      }
      env {
        name  = "REDIS_SSL"
        value = "true"
      }
      env {
        name        = "VIGIE_JWT_SECRET"
        secret_name = "jwt-secret"
      }
      env {
        name  = "VIGIE_AUTH_SECURE_COOKIES"
        value = "true"
      }
      env {
        name  = "VIGIE_PUBLIC_URL"
        value = local.public_url
      }

      dynamic "env" {
        for_each = var.admin_email == "" ? [] : [1]
        content {
          name  = "VIGIE_ADMIN_EMAIL"
          value = var.admin_email
        }
      }
      dynamic "env" {
        for_each = var.admin_email == "" ? [] : [1]
        content {
          name        = "VIGIE_ADMIN_PASSWORD"
          secret_name = "admin-password"
        }
      }

      dynamic "env" {
        for_each = var.mail == null ? tomap({}) : tomap({
          SPRING_MAIL_HOST                                 = var.mail.host
          SPRING_MAIL_PORT                                 = tostring(var.mail.port)
          SPRING_MAIL_USERNAME                             = var.mail.username
          SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH            = "true"
          SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE = "true"
          VIGIE_ALERTS_FROM                                = var.mail.from
        })
        content {
          name  = env.key
          value = env.value
        }
      }
      dynamic "env" {
        for_each = var.mail == null ? [] : [1]
        content {
          name        = "SPRING_MAIL_PASSWORD"
          secret_name = "mail-password"
        }
      }

      liveness_probe {
        transport        = "HTTP"
        port             = 8080
        path             = "/actuator/health"
        initial_delay    = 30
        interval_seconds = 30
      }
    }
  }
}

resource "azurerm_container_app" "worker" {
  name                         = "vigie-worker"
  resource_group_name          = azurerm_resource_group.main.name
  container_app_environment_id = azurerm_container_app_environment.main.id
  revision_mode                = "Single"
  tags                         = local.tags

  secret {
    name  = "redis-url"
    value = "rediss://:${azurerm_redis_cache.main.primary_access_key}@${azurerm_redis_cache.main.hostname}:${azurerm_redis_cache.main.ssl_port}/0"
  }

  # No ingress: the worker only pulls jobs from Redis and makes outbound connections.
  template {
    min_replicas = 1
    max_replicas = 1

    container {
      name   = "worker"
      image  = "${local.registry}/vigie-worker:${var.image_tag}"
      cpu    = 0.25
      memory = "0.5Gi"

      env {
        name        = "VIGIE_REDIS_URL"
        secret_name = "redis-url"
      }
    }
  }
}

resource "azurerm_container_app" "web" {
  name                         = "vigie-web"
  resource_group_name          = azurerm_resource_group.main.name
  container_app_environment_id = azurerm_container_app_environment.main.id
  revision_mode                = "Single"
  tags                         = local.tags

  # The only public endpoint. Container Apps provides the HTTPS certificate.
  ingress {
    external_enabled = true
    target_port      = 3000
    transport        = "auto"

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    # Scales to zero when nobody visits: the first visit after a pause takes a few seconds.
    min_replicas = 0
    max_replicas = 2

    container {
      name   = "web"
      image  = "${local.registry}/vigie-web:${var.image_tag}"
      cpu    = 0.25
      memory = "0.5Gi"
    }
  }
}
