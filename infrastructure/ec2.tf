resource "aws_instance" "jenkins" {
  ami                    = "ami-0f58b397bc5c1f2e8" # Ubuntu 24.04 LTS ap-south-1
  instance_type          = var.ec2_instance_type
  key_name               = var.ec2_key_name
  subnet_id              = aws_subnet.public_1.id
  vpc_security_group_ids = [aws_security_group.jenkins.id]
  iam_instance_profile   = aws_iam_instance_profile.jenkins.name

  root_block_device {
    volume_size = 30
    volume_type = "gp3"
  }

  # Bootstraps entire Jenkins + SonarQube + tools on first boot
  user_data = <<-EOF
    #!/bin/bash
    apt update -y && apt upgrade -y
    apt install -y fontconfig openjdk-17-jdk maven git curl unzip wget docker.io docker-compose

    systemctl start docker && systemctl enable docker
    usermod -aG docker ubuntu

    # Jenkins
    wget -O /usr/share/keyrings/jenkins-keyring.asc \
      https://pkg.jenkins.io/debian-stable/jenkins.io-2023.key
    echo "deb [signed-by=/usr/share/keyrings/jenkins-keyring.asc] \
      https://pkg.jenkins.io/debian-stable binary/" | \
      tee /etc/apt/sources.list.d/jenkins.list
    apt update -y && apt install -y jenkins
    systemctl start jenkins && systemctl enable jenkins
    usermod -aG docker jenkins

    # AWS CLI
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip
    unzip awscliv2.zip && ./aws/install

    # kubectl
    curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
    install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

    # eksctl
    curl -sLO "https://github.com/eksctl-io/eksctl/releases/latest/download/eksctl_Linux_amd64.tar.gz"
    tar -xzf eksctl_Linux_amd64.tar.gz && mv eksctl /usr/local/bin

    # Helm
    curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

    # Trivy
    curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | \
      sh -s -- -b /usr/local/bin

    # SonarQube
    docker run -d \
      --name sonarqube \
      --restart unless-stopped \
      -p 9001:9000 \
      -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true \
      sonarqube:community
  EOF

  tags = { Name = "${var.project_name}-jenkins" }
}

# Elastic IP — Jenkins IP stays same across reboots
resource "aws_eip" "jenkins" {
  instance = aws_instance.jenkins.id
  domain   = "vpc"
  tags     = { Name = "${var.project_name}-jenkins-eip" }
}
