# PayStream Banking Platform

Enterprise inter-bank payment processing system — NEFT, RTGS, IMPS, and UPI
rails — built as a Spring Boot 3.3.0 / Spring Cloud 2023.0.3 / Java 17
microservices platform.

## Architecture

```
                              ┌──────────────────┐
                              │   api-gateway     │  :9000
                              │ (Spring Cloud GW) │
                              └─────────┬─────────┘
                                        │
        ┌───────────────┬──────────────┼──────────────┬───────────────┐
        ▼               ▼              ▼              ▼               ▼
  auth-service    customer-service  account-service  neft/rtgs/   transaction-
   :9005             :9002            :9003          imps/upi-     service :9008
                                                       service
                                                  (:9004/:9012/
                                                   :9006/:9007)

  fraud-detection-service :9009   notification-service :9010   audit-service :9011

         discovery-server :8761            config-server :8888
            (Eureka)                    (Spring Cloud Config)
```

Every service registers with **Eureka** (`discovery-server`) and pulls its
configuration from **Spring Cloud Config** (`config-server`, backed by
`config-repo/`). The **api-gateway** is the single externally-reachable
entrypoint; all other services are internal (`lb://` routing + Feign).

## Services

| Service | Port | Responsibility |
|---|---|---|
| `config-server` | 8888 | Centralized config (native, file-backed by `config-repo/`) |
| `discovery-server` | 8761 | Eureka service registry |
| `api-gateway` | 9000 | Single entrypoint — JWT auth, rate limiting, idempotency, routing |
| `auth-service` | 9005 | Login/register, JWT issuance, roles (CUSTOMER/ADMIN/TELLER/COMPLIANCE_OFFICER/FRAUD_ANALYST) |
| `customer-service` | 9002 | Customer profile + KYC document submission/verification |
| `account-service` | 9003 | Bank accounts, balances, per-account/per-mode limits |
| `neft-service` | 9004 | NEFT transfers — batched settlement (Mon-Sat, 08:00-19:00, every 30 min) |
| `rtgs-service` | 9012 | RTGS transfers — real-time gross settlement (Mon-Fri 07:00-18:00, Sat 07:00-13:00) |
| `imps-service` | 9006 | IMPS transfers — 24x7 real-time, account+IFSC or mobile+MMID |
| `upi-service` | 9007 | UPI PAY/COLLECT/REFUND — VPA registry, PIN auth, Redis-cached VPA lookups |
| `transaction-service` | 9008 | Central ledger — aggregates all settled transactions, generates PDF statements |
| `fraud-detection-service` | 9009 | Rule-based fraud screening (velocity, large-amount, odd-hour) — builds its own local read-model from Kafka events |
| `notification-service` | 9010 | Dispatches customer notifications on settlement/fraud events |
| `audit-service` | 9011 | Append-only audit trail across all domain events |
| `common-lib` | — | Shared event DTOs (not deployed) |

> **Known port collision avoided:** `rtgs-service` originally defaulted to
> 9005 (clashing with `auth-service`) and was moved to **9012**.

## RBI/NPCI Business Rules

| Rail | Window | Limits | Settlement |
|---|---|---|---|
| NEFT | Mon-Sat, 08:00-19:00 | No min, max ₹10,00,000 | Batched every 30 min |
| RTGS | Mon-Fri 07:00-18:00, Sat 07:00-13:00 | Min ₹2,00,000, no max | Real-time |
| IMPS | 24x7 | Min ₹1, max ₹5,00,000 | Real-time |
| UPI | 24x7 | Min ₹1, max ₹1,00,000 | Real-time, PIN-authenticated |

## Kafka Event Topics

See [`docs/asyncapi.yml`](docs/asyncapi.yml) for the full AsyncAPI spec.
Topics are explicitly provisioned (not left to Kafka's auto-create default):

- **dev**: `docker-compose.yml`'s `kafka-topics-init` one-shot container — 3 partitions, replication factor 1.
- **prod**: `k8s/kafka/topics-job.yaml` Kubernetes Job against AWS MSK — 3 partitions, replication factor 3.

Every domain topic has a matching `.DLT` dead-letter topic.

**Wired today:** `account.created`, `payment.{neft,rtgs,imps,upi}.{completed,settled}`, `fraud.alert`.
**Reserved (provisioned, not yet produced/consumed):** `payment.*.initiated`, `customer.kyc.updated`, `notification.send` — see `docs/asyncapi.yml` for why.

## Running Locally

```bash
cd backend
mvn clean install -DskipTests   # builds all 15 modules
docker compose up -d            # postgres, mysql, kafka, redis, all 14 services
```

Postgres/MySQL each get multiple databases on first boot via
`postgres-init/` and `mysql-init/` (mounted to `/docker-entrypoint-initdb.d`).
If you've run this stack before with the old credentials, **drop the
`postgres_data`/`mysql_data` volumes first** — init scripts only run against
an empty data directory.

## Building & Deploying

See [DEPLOYMENT_GUIDE.txt](DEPLOYMENT_GUIDE.txt) for the full Terraform →
Jenkins/GitHub Actions → ArgoCD → EKS pipeline walkthrough.

- **Infra**: `infrastructure/` (Terraform) — EKS, ECR (one repo per service), Jenkins EC2.
- **CI**: `Jenkinsfile` and `.github/workflows/ci.yml` — build the full reactor once, then build/scan/push each of the 14 deployable services to its own ECR repo.
- **CD**: `helm/paystream-service/` — one generic Helm chart parameterized per service (`values/<service>.yaml`) and per environment (`values-{dev,staging,prod}.yaml`). `k8s/argocd/*.yaml` are ArgoCD `ApplicationSet`s that generate one `Application` per service per environment from that same chart.

## Known Limitations

- Fraud detection is **post-hoc** — it consumes the same `payment.*.completed` events that `transaction-service` does and can raise an alert, but cannot synchronously block a transfer before settlement.
- `HIGH_RISK_ACCOUNT` fraud rule type has no backing data source yet.
- `notification-service` and `audit-service` cannot resolve a customer/user identity from most events — no event in the system currently carries `customerId` outward from `customer-service`/`auth-service`.
- Istio (`k8s/istio/`), the NetworkPolicy (`k8s/network-policy/`), and the Vault ServiceAccount (`k8s/vault/`) ship as a single representative template for `account-service` — replicate the pattern for the other 13 services as needed.
