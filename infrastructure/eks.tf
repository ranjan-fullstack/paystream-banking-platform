resource "aws_eks_cluster" "main" {
  name     = var.eks_cluster_name
  role_arn = aws_iam_role.eks_cluster.arn
  version  = "1.35"

  vpc_config {
    subnet_ids = [
      aws_subnet.private_1.id,
      aws_subnet.private_2.id,
      aws_subnet.public_1.id,
      aws_subnet.public_2.id
    ]
    endpoint_private_access = true
    endpoint_public_access  = true
    # Restrict which CIDRs can reach the public API endpoint.
    # Default ["0.0.0.0/0"] is kept here; override via var.eks_public_access_cidrs in prod.
    public_access_cidrs = var.eks_public_access_cidrs
  }

  # Ship API server audit, authenticator, and controller logs to CloudWatch
  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  # Encrypt Kubernetes Secrets at rest with a KMS key
  encryption_config {
    provider {
      key_arn = aws_kms_key.eks_secrets.arn
    }
    resources = ["secrets"]
  }

  depends_on = [aws_iam_role_policy_attachment.eks_cluster_policy]

  tags = { Name = var.eks_cluster_name }
}

# KMS key for envelope encryption of Kubernetes Secrets
resource "aws_kms_key" "eks_secrets" {
  description             = "${var.project_name} EKS secrets encryption"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = { Name = "${var.project_name}-eks-secrets-key" }
}

resource "aws_kms_alias" "eks_secrets" {
  name          = "alias/${var.project_name}-eks-secrets"
  target_key_id = aws_kms_key.eks_secrets.key_id
}

resource "aws_eks_node_group" "workers" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.project_name}-workers"
  node_role_arn   = aws_iam_role.eks_node.arn
  instance_types  = [var.eks_node_instance_type]

  # Nodes go into private subnets — not directly exposed
  subnet_ids = [
    aws_subnet.private_1.id,
    aws_subnet.private_2.id
  ]

  scaling_config {
    desired_size = var.eks_node_desired
    min_size     = var.eks_node_min
    max_size     = var.eks_node_max
  }

  update_config {
    max_unavailable = 1
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.ecr_read_policy
  ]

  tags = { Name = "${var.project_name}-worker" }
}
