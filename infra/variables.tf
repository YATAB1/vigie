variable "subscription_id" {
  description = "Azure subscription ID (az account show --query id -o tsv)."
  type        = string
}

variable "location" {
  description = "Azure region. Canada Central keeps the data in Canada."
  type        = string
  default     = "canadacentral"
}

variable "resource_group_name" {
  description = "Resource group holding every Vigie resource. Deleting it deletes everything."
  type        = string
  default     = "rg-vigie"
}

variable "github_owner" {
  description = "Owner of the GitHub Container Registry images (lowercase)."
  type        = string
  default     = "yatab1"
}

variable "image_tag" {
  description = "Image tag to deploy: latest, or a commit SHA for a precise version."
  type        = string
  default     = "latest"
}

variable "admin_email" {
  description = "Optional: email of an administrator account created at startup."
  type        = string
  default     = ""
}

variable "admin_password" {
  description = "Optional: password of that administrator (12 characters or more)."
  type        = string
  default     = ""
  sensitive   = true
}

variable "reporting_ip" {
  description = "Optional: your public IP, allowed to reach PostgreSQL directly (Power BI)."
  type        = string
  default     = ""
}

variable "mail" {
  description = "Optional SMTP server for alert emails. Without it, alerts are only logged."
  type = object({
    host     = string
    port     = number
    username = string
    from     = string
  })
  default = null
}

# Kept apart from "mail": Terraform refuses sensitive values in for_each, which builds the env list.
variable "mail_password" {
  description = "Password or API key of the SMTP server."
  type        = string
  default     = ""
  sensitive   = true
}
