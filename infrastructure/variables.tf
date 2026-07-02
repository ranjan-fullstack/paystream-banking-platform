variable "aws_region" {
  description = "AWS region"
  default     = "ap-south-1"
}

variable "project_name" {
  description = "Project name prefix for all resources"
  default     = "paystream"
}

variable "eks_cluster_name" {
  description = "EKS cluster name"
  default     = "paystream-cluster"
}

variable "ec2_instance_type" {
  description = "Jenkins EC2 instance type"
  default     = "t3.medium"
}

variable "eks_node_instance_type" {
  description = "EKS worker node instance type"
  default     = "t3.medium"
}

variable "eks_node_desired" {
  description = "Desired number of EKS worker nodes"
  default     = 2
}

variable "eks_node_min" {
  description = "Minimum EKS worker nodes"
  default     = 1
}

variable "eks_node_max" {
  description = "Maximum EKS worker nodes"
  default     = 4
}

variable "ec2_key_name" {
  description = "EC2 key pair name (must exist in AWS)"
  default     = "devops-key"
}

variable "eks_public_access_cidrs" {
  description = "CIDRs allowed to reach the EKS public API endpoint. Restrict to your office/VPN in production."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "admin_cidr" {
  description = "CIDR allowed SSH/admin access to Jenkins. Restrict to your office/VPN in production."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "github_repo" {
  description = "GitHub repo in owner/repo format — used to scope the GitHub Actions OIDC trust policy"
  default     = "ranjan-fullstack/paystream-banking-platform"
}
