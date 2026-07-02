terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Remote state — keeps infra state in S3, locking via DynamoDB
  # Run ONCE before terraform init:
  #   aws s3 mb s3://paystream-terraform-state --region ap-south-1
  #   aws dynamodb create-table --table-name paystream-terraform-lock \
  #     --attribute-definitions AttributeName=LockID,AttributeType=S \
  #     --key-schema AttributeName=LockID,KeyType=HASH \
  #     --billing-mode PAY_PER_REQUEST --region ap-south-1
  backend "s3" {
    bucket         = "paystream-terraform-state-ranjan-198758256599"
    key            = "paystream/terraform.tfstate"
    region         = "ap-south-1"
    dynamodb_table = "paystream-terraform-lock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region
}
