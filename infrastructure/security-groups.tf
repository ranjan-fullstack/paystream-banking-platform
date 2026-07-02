resource "aws_security_group" "jenkins" {
  name        = "${var.project_name}-jenkins-sg"
  description = "Jenkins + SonarQube server"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "SSH - restrict var.admin_cidr to your office/VPN in production"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.admin_cidr
  }

  ingress {
    description = "Jenkins - restrict var.admin_cidr to your office/VPN in production"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = var.admin_cidr
  }

  ingress {
    description = "SonarQube - restrict var.admin_cidr to your office/VPN in production"
    from_port   = 9001
    to_port     = 9001
    protocol    = "tcp"
    cidr_blocks = var.admin_cidr
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-jenkins-sg" }
}
