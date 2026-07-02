# Secrets Setup

Config files in `config-repo/` use `${ENV_VAR}` placeholders. No secrets are stored in git.

## Required environment variables

| Variable | Used by | Description |
|---|---|---|
| `JWT_SECRET` | api-gateway, auth-service | HS256 signing key — minimum 256-bit (32-char) random string |
| `AUTH_DB_USERNAME` | auth-service | MySQL username for `paystream_auth` |
| `AUTH_DB_PASSWORD` | auth-service | MySQL password for `paystream_auth` |
| `ACCOUNT_DB_USERNAME` | account-service | PostgreSQL username for `paystream_accounts` |
| `ACCOUNT_DB_PASSWORD` | account-service | PostgreSQL password for `paystream_accounts` |
| `NEFT_DB_USERNAME` | neft-service | PostgreSQL username for `paystream_neft` |
| `NEFT_DB_PASSWORD` | neft-service | PostgreSQL password for `paystream_neft` |
| `RTGS_DB_USERNAME` | rtgs-service | PostgreSQL username for `paystream_rtgs` |
| `RTGS_DB_PASSWORD` | rtgs-service | PostgreSQL password for `paystream_rtgs` |
| `IMPS_DB_USERNAME` | imps-service | PostgreSQL username for `paystream_imps` |
| `IMPS_DB_PASSWORD` | imps-service | PostgreSQL password for `paystream_imps` |
| `UPI_DB_USERNAME` | upi-service | PostgreSQL username for `paystream_upi` |
| `UPI_DB_PASSWORD` | upi-service | PostgreSQL password for `paystream_upi` |
| `TRANSACTION_DB_USERNAME` | transaction-service | PostgreSQL username for `paystream_transactions` |
| `TRANSACTION_DB_PASSWORD` | transaction-service | PostgreSQL password for `paystream_transactions` |
| `FRAUD_DB_USERNAME` | fraud-detection-service | PostgreSQL username for `paystream_fraud` |
| `FRAUD_DB_PASSWORD` | fraud-detection-service | PostgreSQL password for `paystream_fraud` |
| `AUDIT_DB_USERNAME` | audit-service | PostgreSQL username for `paystream_audit` |
| `AUDIT_DB_PASSWORD` | audit-service | PostgreSQL password for `paystream_audit` |
| `CUSTOMER_DB_USERNAME` | customer-service | MySQL username for `paystream_customers` |
| `CUSTOMER_DB_PASSWORD` | customer-service | MySQL password for `paystream_customers` |
| `NOTIFICATION_DB_USERNAME` | notification-service | MySQL username for `paystream_notifications` |
| `NOTIFICATION_DB_PASSWORD` | notification-service | MySQL password for `paystream_notifications` |
| `KEYSTORE_STORE_PASSWORD` | customer-service | Password to open `paystream-keystore.jceks` (HSM simulation keystore) |
| `KEYSTORE_KEY_PASSWORD` | customer-service | Password for the `pii-encryption-key` entry inside the keystore — in production the keystore itself lives in Vault/HSM, never on disk |

## Local development

Create `.env` in the project root (already in `.gitignore`) and source it before starting services:

```bash
export JWT_SECRET="change-me-to-a-32-char-random-string"
# PostgreSQL services (default: postgres / postgres for local Docker)
export ACCOUNT_DB_USERNAME="postgres"      && export ACCOUNT_DB_PASSWORD="postgres"
export NEFT_DB_USERNAME="postgres"         && export NEFT_DB_PASSWORD="postgres"
export RTGS_DB_USERNAME="postgres"         && export RTGS_DB_PASSWORD="postgres"
export IMPS_DB_USERNAME="postgres"         && export IMPS_DB_PASSWORD="postgres"
export UPI_DB_USERNAME="postgres"          && export UPI_DB_PASSWORD="postgres"
export TRANSACTION_DB_USERNAME="postgres"  && export TRANSACTION_DB_PASSWORD="postgres"
export FRAUD_DB_USERNAME="postgres"        && export FRAUD_DB_PASSWORD="postgres"
export AUDIT_DB_USERNAME="postgres"        && export AUDIT_DB_PASSWORD="postgres"
# MySQL services (default: root / root for local Docker)
export AUTH_DB_USERNAME="root"             && export AUTH_DB_PASSWORD="root"
export CUSTOMER_DB_USERNAME="root"         && export CUSTOMER_DB_PASSWORD="root"
export NOTIFICATION_DB_USERNAME="root"     && export NOTIFICATION_DB_PASSWORD="root"
# Keystore passwords (HSM simulation — keystore file is in src/main/resources for dev only)
export KEYSTORE_STORE_PASSWORD="paystream-keystore-password"
export KEYSTORE_KEY_PASSWORD="paystream-key-password"
```

Or with Docker Compose, pass them via an `env_file: .env` reference in each service block.

## Kubernetes / production

Secrets are injected at pod startup via Kubernetes Secrets or AWS Secrets Manager (through the AWS Secrets and Configuration Provider for Secrets Store CSI Driver). The Helm chart's `values/<service>.yaml` references secret keys; the actual values live in the cluster — never in this repository.

For the EKS setup documented in `DEPLOYMENT_GUIDE.txt`, create the secret with:

```bash
kubectl create secret generic paystream-secrets \
  --from-literal=JWT_SECRET="<value>" \
  --from-literal=ACCOUNT_DB_USERNAME="<value>" \
  --from-literal=ACCOUNT_DB_PASSWORD="<value>" \
  --from-literal=NEFT_DB_USERNAME="<value>" \
  --from-literal=NEFT_DB_PASSWORD="<value>" \
  --from-literal=RTGS_DB_USERNAME="<value>" \
  --from-literal=RTGS_DB_PASSWORD="<value>" \
  --from-literal=IMPS_DB_USERNAME="<value>" \
  --from-literal=IMPS_DB_PASSWORD="<value>" \
  --from-literal=UPI_DB_USERNAME="<value>" \
  --from-literal=UPI_DB_PASSWORD="<value>" \
  --from-literal=TRANSACTION_DB_USERNAME="<value>" \
  --from-literal=TRANSACTION_DB_PASSWORD="<value>" \
  --from-literal=FRAUD_DB_USERNAME="<value>" \
  --from-literal=FRAUD_DB_PASSWORD="<value>" \
  --from-literal=AUDIT_DB_USERNAME="<value>" \
  --from-literal=AUDIT_DB_PASSWORD="<value>" \
  --from-literal=AUTH_DB_USERNAME="<value>" \
  --from-literal=AUTH_DB_PASSWORD="<value>" \
  --from-literal=CUSTOMER_DB_USERNAME="<value>" \
  --from-literal=CUSTOMER_DB_PASSWORD="<value>" \
  --from-literal=NOTIFICATION_DB_USERNAME="<value>" \
  --from-literal=NOTIFICATION_DB_PASSWORD="<value>" \
  --from-literal=KEYSTORE_STORE_PASSWORD="<value>" \
  --from-literal=KEYSTORE_KEY_PASSWORD="<value>" \
  -n paystream-prod
```

The Deployment manifests expose these as environment variables via `envFrom.secretRef`.
