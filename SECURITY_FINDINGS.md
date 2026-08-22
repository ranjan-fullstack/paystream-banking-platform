# Security Findings — Known, Accepted (2026-08-22)

Tracks the state of every CVE-flagging dependency identified this session via SonarCloud +
Trivy, after the Spring Boot 3.3.0 → 3.5.16 / Spring Cloud 2023.0.3 → 2025.0.3 bump
(`72f3e8d`) and the `mysql-connector-java` → `mysql-connector-j` coordinate migration
(`6b02988`). Starting point was 27 unique vulnerable libraries / 5 CRITICAL; this doc covers
what's left.

## Fixed this session

- **mysql-connector-java CVE-2023-22102** — migrated `auth-service`, `customer-service`,
  `notification-service` from the deprecated `mysql:mysql-connector-java:8.0.33` coordinate
  to `com.mysql:mysql-connector-j:9.7.0`. The CVE was already patched in 8.0.33; Trivy's
  advisory data tracks fixes against the new reverse-DNS-compliant GAV the artifact was
  relocated to, so it kept flagging the old coordinate regardless of version. Verified via
  full `mvn test` on all three modules (auth-service's Testcontainers-backed MySQL
  integration tests included). Commit `6b02988`.

## Deferred — blocked on Spring Boot's managed BOM

These four are all transitive dependencies whose versions Spring Boot 3.5.16 pins itself.
Overriding any of them means an explicit `dependencyManagement` override *outside* Boot's
managed versions (same pattern already used for `bcprov-jdk18on` in the root `pom.xml`),
which risks introducing a version Boot's own integration tests never exercised. That's a
correctness/compatibility risk, not a quick fix — deserves a dedicated pass, not the tail
end of a long session. Currently one patch version behind, tracked for next session.

| Dependency | Resolved version | Pulled in via |
|---|---|---|
| `io.netty:netty-codec` / `netty-codec-http` / `netty-codec-http2` / `netty-codec-dns` / `netty-codec-socks` | 4.1.135.Final | transitively, via Spring Kafka / AMQP client stack |
| `com.rabbitmq:amqp-client` | 5.25.0 | `spring-cloud-starter-bus-amqp` (notification-service, customer-service) |
| `org.apache.httpcomponents.core5:httpcore5` / `httpcore5-h2` | 5.3.6 | transitively, via Spring Cloud HTTP client stack |
| `org.postgresql:postgresql` | 42.7.11 | account-service, fraud-detection-service, transaction-service, audit-service, upi-service, imps-service, rtgs-service, neft-service |

## Deferred — attempted, reverted (not BOM-blocked, but not "cheap" either)

- **langchain / langchain-community (ai-service, Python)** — attempted bump
  (`langchain` 0.2.6→0.3.30, `langchain-community` 0.2.6→0.3.27, plus the cascade:
  `langchain-ollama` 0.1.3→0.3.10, `langchain-groq` 0.1.9→0.3.8, `groq` 0.9.0→0.30.0,
  `pydantic-settings` 2.3.4→2.4.0). Hit an unresolvable pip conflict: the bumped `langchain`
  stack's transitive `ollama` dependency requires `pydantic>=2.9`, but `ai-service` pins
  `pydantic==2.7.4` directly, which is foundational to the FastAPI service's request/response
  models and settings. Since pip fails atomically (no partial install), the only way through
  is bumping `pydantic` itself — a change to shared, foundational service code, not an
  isolated patch bump. Reverted cleanly (`git checkout -- ai-service/requirements.txt`,
  confirmed clean diff, nothing was ever installed into the venv). Recommend treating this the
  same as the Netty/RabbitMQ/HttpCore5/Postgres items: worth a dedicated pass that also
  verifies the ai-service test suite against the new pydantic major-adjacent version, not a
  same-session patch.

## Current honest count

- Before tonight: 27 unique vulnerable libraries, 5 CRITICAL
- After Boot 3.5.16 / Spring Cloud 2025.0.3 bump: 10 unique, 0 CRITICAL
- After mysql-connector-j fix (this doc): 9 unique, 0 CRITICAL
- Remaining, all deferred per above: 4 Java (Netty, RabbitMQ, HttpCore5, PostgreSQL) + 2
  Python (langchain, langchain-community) = 6 items, none CRITICAL, all HIGH/MEDIUM per prior
  SonarCloud/Trivy runs.

Next full CI run (`.github/workflows/ci.yml`'s `security-scan` job) will re-confirm this
count against live Trivy + advisory data — the fs-scan step is deliberately configured with
`exit-code: 1` on CRITICAL/HIGH (see [ci.yml:81-89](.github/workflows/ci.yml#L81-L89)), so it
is expected to keep failing until these 6 are addressed or explicitly ignored via
`.trivyignore`.
