output "jenkins_public_ip" {
  description = "Jenkins server public IP — open http://<ip>:8080"
  value       = aws_eip.jenkins.public_ip
}

output "sonarqube_url" {
  description = "SonarQube URL"
  value       = "http://${aws_eip.jenkins.public_ip}:9001"
}

output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.main.name
}

output "eks_cluster_endpoint" {
  description = "EKS API server endpoint"
  value       = aws_eks_cluster.main.endpoint
}

output "ecr_repository_url" {
  description = "ECR repository URL — use in Jenkinsfile"
  value       = aws_ecr_repository.product_service.repository_url
}

output "paystream_ecr_repository_urls" {
  description = "ECR repository URLs for every PayStream microservice, keyed by service name"
  value       = { for name, repo in aws_ecr_repository.paystream : name => repo.repository_url }
}

output "kubeconfig_command" {
  description = "Run this to configure kubectl"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${var.eks_cluster_name}"
}

output "oidc_provider_arn" {
  description = "EKS OIDC provider ARN — use as Federated principal in IRSA trust policies"
  value       = aws_iam_openid_connect_provider.eks.arn
}

output "oidc_provider_url" {
  description = "EKS OIDC provider URL (no https://) — use in IRSA Condition keys"
  value       = local.oidc_provider_url
}

output "eks_secrets_kms_key_arn" {
  description = "KMS key ARN used for EKS Secrets envelope encryption"
  value       = aws_kms_key.eks_secrets.arn
}

output "github_actions_terraform_role_arn" {
  description = "Add this as AWS_TERRAFORM_ROLE_ARN secret in GitHub → Settings → Secrets"
  value       = aws_iam_role.github_actions_terraform.arn
}

output "github_actions_ci_role_arn" {
  description = "Add this as AWS_CI_ROLE_ARN secret in GitHub → Settings → Secrets"
  value       = aws_iam_role.github_actions_ci.arn
}
