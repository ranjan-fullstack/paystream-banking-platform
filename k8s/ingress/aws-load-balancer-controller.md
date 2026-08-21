# AWS Load Balancer Controller + TLS Ingress (Stage 9)

## IAM (IRSA)

New role, separate from `paystream-dev-secrets-read` (Stage 3) and
`paystream-cloudwatch-agent-role` (Stage 6) — same least-privilege,
one-role-per-purpose pattern.

- **Role**: `eksctl-paystream-eks-addon-iamserviceaccount-kube-system-aws-load-balancer-controller-Role*`
  (created by `eksctl create iamserviceaccount`, trust-bound to
  `system:serviceaccount:kube-system:aws-load-balancer-controller`)
- **Policy**: `paystream-alb-controller-policy`
  (`arn:aws:iam::198758256599:policy/paystream-alb-controller-policy`) --
  the official AWS-published IAM policy for the controller
  (`iam_policy.json` from the `kubernetes-sigs/aws-load-balancer-controller`
  repo), covering ELBv2/EC2/ACM/WAF describe+mutate actions scoped to
  managing ALB/NLB resources the controller creates.

## Recreate from scratch

```bash
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/main/docs/install/iam_policy.json

aws iam create-policy --policy-name paystream-alb-controller-policy \
  --policy-document file://iam_policy.json

eksctl create iamserviceaccount \
  --cluster paystream-eks --region ap-south-1 \
  --namespace kube-system --name aws-load-balancer-controller \
  --attach-policy-arn arn:aws:iam::198758256599:policy/paystream-alb-controller-policy \
  --approve

helm repo add eks https://aws.github.io/eks-charts
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  --namespace kube-system \
  --set clusterName=paystream-eks \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller \
  --set region=ap-south-1 \
  --set vpcId=<vpc-id>
```

Subnet auto-discovery needs no manual tagging here -- eksctl already tags
public subnets `kubernetes.io/role/elb=1` and private subnets
`kubernetes.io/role/internal-elb=1` at cluster creation time.

## TLS certificate

Self-signed (`CN=api.paystream.internal`), generated locally and imported
directly into ACM -- there's no owned public domain to get a real
publicly-trusted cert issued against for this dev cluster. ACM treats an
imported cert the same as an issued one for ALB HTTPS listeners; it just
doesn't auto-renew, and browsers will show the expected untrusted-cert
warning. Noting this honestly rather than presenting it as a real
trusted cert.

```bash
openssl genrsa -out api-paystream.key 2048
openssl req -x509 -key api-paystream.key -out api-paystream.crt -days 365 \
  -subj "/C=IN/ST=Karnataka/L=Bangalore/O=PayStream/CN=api.paystream.internal"

aws acm import-certificate \
  --certificate fileb://api-paystream.crt \
  --private-key fileb://api-paystream.key \
  --region ap-south-1
# -> arn:aws:acm:ap-south-1:198758256599:certificate/282e42b5-87bf-4c76-8c64-4eddf8460459
```

The cert/key files were deleted locally immediately after import -- only
the ACM-held copy persists.

## Verification performed

- `kubectl get ingress -n paystream-dev` shows a real ALB DNS name
  (`k8s-paystrea-apigatew-3c891d08ab-1731420963.ap-south-1.elb.amazonaws.com`)
- `aws elbv2 describe-listeners` confirms real HTTP:80 and HTTPS:443
  listeners, HTTPS bound to the imported cert
- `curl` through the real ALB DNS name: HTTP request gets a 301 redirect
  to HTTPS (ssl-redirect annotation working); HTTPS request returns a
  real 200 with `{"status":"UP",...}` from api-gateway's actual
  `/actuator/health` endpoint
