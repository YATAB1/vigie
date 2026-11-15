output "web_url" {
  description = "Public address of Vigie."
  value       = "https://${azurerm_container_app.web.ingress[0].fqdn}"
}

output "postgres_host" {
  description = "PostgreSQL server, for Power BI or psql."
  value       = azurerm_postgresql_flexible_server.main.fqdn
}

output "postgres_admin_user" {
  value = azurerm_postgresql_flexible_server.main.administrator_login
}

output "postgres_admin_password" {
  description = "Read it with: terraform output -raw postgres_admin_password"
  value       = random_password.database.result
  sensitive   = true
}
