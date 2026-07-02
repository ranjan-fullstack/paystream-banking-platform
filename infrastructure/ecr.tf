resource "aws_ecr_repository" "product_service" {
  name                 = "product-service"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true # ECR scans every pushed image automatically
  }

  tags = { Name = "${var.project_name}-product-service" }
}

# Keep only last 10 images — saves ECR storage costs
resource "aws_ecr_lifecycle_policy" "product_service" {
  repository = aws_ecr_repository.product_service.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# ─────────────────────────────────────────────────────────────────────────────
# PayStream Banking Platform — one ECR repo per microservice.
# Additive only: existing product-service repo/state above is untouched so
# the live cluster/backend are not disturbed.
# ─────────────────────────────────────────────────────────────────────────────
variable "paystream_services" {
  description = "PayStream microservice names — each gets its own ECR repository"
  type        = list(string)
  default = [
    "config-server",
    "discovery-server",
    "api-gateway",
    "auth-service",
    "customer-service",
    "account-service",
    "neft-service",
    "rtgs-service",
    "imps-service",
    "upi-service",
    "transaction-service",
    "fraud-detection-service",
    "notification-service",
    "audit-service",
  ]
}

resource "aws_ecr_repository" "paystream" {
  for_each = toset(var.paystream_services)

  name                 = each.value
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = { Name = "paystream-${each.value}" }
}

# Keep only last 10 images per PayStream service — same retention policy as product-service
resource "aws_ecr_lifecycle_policy" "paystream" {
  for_each = aws_ecr_repository.paystream

  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
