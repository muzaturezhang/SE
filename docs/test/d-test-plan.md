# Holiday Resort Booking System — D Test Plan

Status: internal implementation verification for the merged A/B/C/D baseline, not final user acceptance evidence.
Owner: D (Quality, Notification and Persistence Lead).
Requirements baseline: `requirements-spec(2).md` v1.0; design reference: `design-spec(2).md` v0.1.

## 1. Scope

This plan covers D-owned requirements:

| Requirement | Covered behaviour |
| --- | --- |
| FR-REM-001 | Shanghai-time one-day-before reminder scheduling |
| FR-REM-002 | Privacy-safe reminder rendering and delivery failure classification |
| FR-REM-003 | Cancellation and booking-change rescheduling |
| FR-DB-001 | H2 and MySQL 8 persistence, repository adapters and Flyway migrations |
| FR-DB-002 | Schema constraints, idempotency, history and no destructive business-row updates |
| FR-DB-003 | Canonical lock order and cross-module transactional boundaries |
| FR-DB-004 | JUnit, clean reset, JaCoCo, CI and reproducible verification |
| NFR-SEC-01 / NFR-PRI-01 | No credentials or personal data in D logs; BCrypt-only account storage |
| NFR-DAT-01 / NFR-TIM-01 | Integer-fen amounts, atomic writes and shared Shanghai clock |

## 2. Test levels

1. Domain unit tests: reminder timing/state transitions, price snapshots and terminal states.
2. Repository/adaptor tests: H2 schema contracts, JDBC mappings, constraints and audit history.
3. Application integration tests: Booking → inventory → Booking persistence → reminder lifecycle.
4. MySQL 8 persistence integration test: enabled only when `MYSQL_URL` is present; CI runs this against a real `mysql:8.0` service.
5. Failure and concurrency tests: versioned reminder claims, stale SENDING lease recovery, duplicate callbacks, duplicate idempotency keys and rollback paths.
6. Evidence checks: Surefire, JaCoCo CSV/HTML, packaged JAR and GitHub Actions artifacts.

## 3. Environment

- Java: Temurin/OpenJDK 17, source and target 17.
- Maven: 3.9.11 via the Maven Wrapper.
- Default test database: H2 2.3 with a fresh in-memory schema.
- MySQL profile test: H2 running in MySQL compatibility mode locally; real MySQL 8 in CI.
- Email: `MockEmailTransport` by default; `smtp-email` is opt-in and requires environment-only credentials.
- Payment: `SandboxPaymentProvider` by default and in CI; no real funds or production payment credentials are used.

## 4. Entry criteria

- The merged source compiles on Java 17.
- Flyway migrations V1–V8 apply to a clean schema.
- Required MySQL or SMTP environment variables are provided only when the corresponding profile is intentionally enabled.
- No credentials, personal email addresses or temporary files are present in the test classpath.

## 5. Exit criteria

- `scripts/test.ps1` or `mvnw clean verify` passes.
- Unit/integration tests have zero failures and zero errors.
- The only skipped test is the environment-gated `MySqlLiveIntegrationTest` when no real MySQL URL is supplied.
- JaCoCo BUNDLE line coverage remains at or above 90%.
- The MySQL CI job is green and the default CI job uploads Surefire/JaCoCo artifacts.
- Known gaps are recorded in `test-report.md` rather than hidden.

## 6. Test cases

The detailed register is in `test-case-register.csv`. Test IDs use `TC-D-*`; each case links to a requirement and implementation class.

## 7. Reproduction

```powershell
$env:JAVA_HOME = '<project>/.cache/jdk17/extracted/jdk-17.0.20.1+1'
./scripts/test.ps1
```

For the live MySQL smoke test:

```powershell
$env:MYSQL_URL = 'jdbc:mysql://127.0.0.1:3306/resort?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
$env:MYSQL_USERNAME = 'root'
$env:MYSQL_PASSWORD = '<local password>'
./scripts/maven.ps1 --batch-mode --no-transfer-progress -Dtest=MySqlLiveIntegrationTest test
```

Do not commit real passwords, provider keys or recipient addresses.
