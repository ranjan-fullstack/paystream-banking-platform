# CloudWatch Container Insights (Stage 6)

Deployed via the AWS-managed EKS add-on `amazon-cloudwatch-observability`
(bundles Fluent Bit for logs + the CloudWatch Agent for metrics), rather than
hand-rolled Fluent Bit DaemonSet manifests — this is the add-on AWS now
recommends over the older Container Insights quickstart YAML.

This add-on is an EKS API-level resource (`aws eks create-addon`), not a
k8s manifest to `kubectl apply` — there is nothing to commit for the add-on
itself. What's captured here is the IAM setup and the exact commands, so the
whole thing is reproducible.

## IAM (IRSA)

New IAM role, not present before this stage:

- **Role**: `paystream-cloudwatch-agent-role`
  (`arn:aws:iam::198758256599:role/paystream-cloudwatch-agent-role`)
- **Trust policy**: federated to this cluster's OIDC provider, scoped to
  `system:serviceaccount:amazon-cloudwatch:cloudwatch-agent`
- **Attached policy**: AWS managed `CloudWatchAgentServerPolicy`
  (`arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy`) — grants
  `cloudwatch:PutMetricData`, `logs:CreateLogGroup/Stream`, `logs:PutLogEvents`,
  `ec2:DescribeTags`, `ssm:GetParameter` (for the CW agent config)

This is a new permission surface distinct from the earlier
`paystream-dev-secrets-read` policy used for application secrets — Container
Insights needs its own IAM identity because it runs in `amazon-cloudwatch`,
not `paystream-dev`, and needs CloudWatch/EC2 describe permissions the app
pods have no reason to hold.

## Recreate from scratch

```bash
OIDC_URL=$(aws eks describe-cluster --name paystream-eks --region ap-south-1 \
  --query 'cluster.identity.oidc.issuer' --output text | sed 's|https://||')

cat > cwagent-trust-policy.json <<JSON
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Federated": "arn:aws:iam::198758256599:oidc-provider/${OIDC_URL}"},
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {"StringEquals": {
      "${OIDC_URL}:sub": "system:serviceaccount:amazon-cloudwatch:cloudwatch-agent",
      "${OIDC_URL}:aud": "sts.amazonaws.com"
    }}
  }]
}
JSON

aws iam create-role --role-name paystream-cloudwatch-agent-role \
  --assume-role-policy-document file://cwagent-trust-policy.json

aws iam attach-role-policy --role-name paystream-cloudwatch-agent-role \
  --policy-arn arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy

aws eks create-addon \
  --cluster-name paystream-eks --region ap-south-1 \
  --addon-name amazon-cloudwatch-observability \
  --addon-version v6.5.0-eksbuild.1 \
  --service-account-role-arn arn:aws:iam::198758256599:role/paystream-cloudwatch-agent-role
```

## Verification performed

- `kubectl get pods -n amazon-cloudwatch` — cloudwatch-agent (1 per node) +
  fluent-bit (1 per node) + controller-manager, all `1/1 Running`
- Log groups created: `/aws/containerinsights/paystream-eks/application`,
  `.../dataplane`, `.../host`, `.../performance`
- Generated real traffic against both `api-gateway` and `account-service`
  (port-forwarded `/actuator/health` requests), then confirmed via
  CloudWatch Logs Insights that both services' log lines are present and
  searchable in `/aws/containerinsights/paystream-eks/application`, with
  real timestamps matching the traffic just generated.
