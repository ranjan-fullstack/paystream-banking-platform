# PayStream Banking Platform — API Documentation

This folder contains the API contracts and tooling for every microservice in the
PayStream Banking Platform: OpenAPI 3.0 specs per service, and a single Postman
collection that exercises the platform end-to-end through the API Gateway.

## Services and ports

| Service | Port | Spec |
|---|---|---|
| API Gateway (entry point) | 9000 | [openapi/api-gateway.yaml](openapi/api-gateway.yaml) |
| auth-service | 9005 | [openapi/auth-service.yaml](openapi/auth-service.yaml) |
| customer-service | 9002 | [openapi/customer-service.yaml](openapi/customer-service.yaml) |
| account-service | 9003 | [openapi/account-service.yaml](openapi/account-service.yaml) |
| neft-service | 9004 | [openapi/neft-service.yaml](openapi/neft-service.yaml) |
| imps-service | 9006 | [openapi/imps-service.yaml](openapi/imps-service.yaml) |
| upi-service | 9007 | [openapi/upi-service.yaml](openapi/upi-service.yaml) |
| transaction-service | 9008 | [openapi/transaction-service.yaml](openapi/transaction-service.yaml) |
| fraud-detection-service | 9009 | [openapi/fraud-detection-service.yaml](openapi/fraud-detection-service.yaml) |
| notification-service | 9010 | [openapi/notification-service.yaml](openapi/notification-service.yaml) |
| audit-service | 9011 | [openapi/audit-service.yaml](openapi/audit-service.yaml) |
| rtgs-service | 9012 | [openapi/rtgs-service.yaml](openapi/rtgs-service.yaml) |

All client traffic should go through the **API Gateway on port 9000**
(`http://localhost:9000`), which handles JWT authentication, role-based
authorization and rate limiting before routing to the service that owns the
request. Each spec also lists the service's own port for direct/local testing.

## Authentication model

1. Call `POST /auth/v1/register` then `POST /auth/v1/login` (auth-service,
   public, no token required) to obtain an `accessToken` / `refreshToken` pair.
2. Send `Authorization: Bearer <accessToken>` on every other request. The
   gateway validates the token and forwards the user's role downstream via an
   `X-USER-ROLE` header — you never need to set that header yourself.
3. When the access token expires, call `POST /auth/v1/refresh` with the
   `refreshToken` to get a new access token.
4. Some endpoints are further role-gated (e.g. opening an account requires
   ADMIN or TELLER; reviewing fraud alerts requires FRAUD_ANALYST or ADMIN).
   These restrictions are called out in each spec's endpoint description.

## Importing the Postman collection

1. Open Postman → **Import** → select
   [postman/PayStream-Banking-Platform.postman_collection.json](postman/PayStream-Banking-Platform.postman_collection.json).
2. The collection ships with these variables (Collection → Variables tab):
   - `base_url` — defaults to `http://localhost:9000` (the gateway)
   - `jwt_token` — empty; auto-filled after **Auth → Login**
   - `customer_id` — empty; auto-filled after **Customer → Register Customer**
   - `account_number` / `account_id` — empty; auto-filled after **Account → Open Account**
   - several other helper variables (`vpa`, `neft_reference`, `rtgs_reference`,
     `imps_reference`, `upi_txn_id`, `alert_id`, `rule_id`, etc.) that are
     auto-filled by the request that creates the corresponding resource
3. No manual setup beyond running requests in order is required — every
   request that returns an ID/reference number saves it to a collection
   variable via its **Tests** tab so the next request can use it.

## How to use

Run requests top-to-bottom within a folder; later requests depend on
variables set by earlier ones:

1. **🔐 Auth → Login** — populates `{{jwt_token}}` (and `{{refresh_token}}`).
   Every other request sends `Authorization: Bearer {{jwt_token}}`.
2. **👤 Customer → Register Customer** — populates `{{customer_id}}`.
3. **🏦 Account → Open Account** — populates `{{account_number}}` and `{{account_id}}`.
4. Any of **💸 NEFT**, **🏛️ RTGS**, **⚡ IMPS**, **📱 UPI** can now move money
   using `{{account_number}}` / `{{customer_id}}` / `{{vpa}}`.
5. **📊 Transactions**, **🚨 Fraud Detection**, **🔔 Notifications**, **📋 Audit**
   are read-side folders you can call any time after step 3 to inspect what
   happened.

Notes:
- **NEFT** only accepts transfers Mon–Sat, 08:00–19:00 (settles in half-hourly
  batches); outside that window the API returns `422 NEFT_WINDOW_CLOSED`.
- **RTGS** requires a minimum amount of ₹2,00,000 per transfer.
- Endpoints documented as ADMIN/TELLER/FRAUD_ANALYST/COMPLIANCE_OFFICER-only
  will return `403 FORBIDDEN` if you're logged in as a `CUSTOMER`-role user —
  register/login a user with the appropriate `role` first.

## Sample end-to-end workflow

```
1. POST /auth/v1/register        { username, password, role: "ADMIN" }
2. POST /auth/v1/login            -> accessToken, refreshToken
3. POST /api/v1/customers/register
     Authorization: Bearer <accessToken>
                                   -> customerId
4. POST /api/v1/accounts
     Authorization: Bearer <accessToken>
     { customerId, accountType: "SAVINGS", branchCode }
                                   -> accountNumber, accountId
5. POST /api/v1/neft/transfer
     Authorization: Bearer <accessToken>
     { senderAccountNumber: accountNumber, beneficiaryAccountNumber, amount, ... }
                                   -> neftReferenceNumber, status: QUEUED
6. GET  /api/v1/neft/{neftReferenceNumber}
                                   -> status: COMPLETED (once the next batch runs)
7. GET  /api/v1/transactions/account/{accountNumber}
                                   -> unified transaction history including the NEFT transfer
```

The Postman collection's **Auth → Customer → Account → NEFT** folders
(in that order) reproduce this exact flow with the variable auto-fill
described above.
