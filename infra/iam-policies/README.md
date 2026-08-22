# IAM policies (not yet Terraform-managed)

These are IAM policies created ad hoc via `aws iam create-policy` for IRSA
service accounts, tracked here as checked-in documents so they can be
diffed and recreated without redoing the reasoning from scratch each time.

Not wired into `infrastructure/` (Terraform) — that's a separate, larger
migration. This is the minimum fix: a file in git instead of a one-off CLI
command that only exists in a chat transcript.

- `paystream-jenkins-ecr-push.json` — attached to the `jenkins-agent` IRSA
  service account (`jenkins` namespace). Least-privilege ECR push + the
  Trivy scan stage's read-back, scoped to the 6 paystream service repos.
  Recreate with:
  ```bash
  aws iam create-policy --policy-name paystream-jenkins-ecr-push \
    --policy-document file://infra/iam-policies/paystream-jenkins-ecr-push.json
  ```

`paystream-dev-secrets-read` and `paystream-alb-controller-policy` predate
this convention and aren't tracked here yet — same gap, not yet closed.
