# Holiday Resort Booking System — Final Engineering Verification Report

Date: 2026-11-14 (Asia/Shanghai)
Status: engineering verification complete for the integrated repository; external SMTP delivery and Q5/Q7 user-test evidence are still pending and are not claimed as complete.

## 1. Environment

- Execution host: Windows, PowerShell.
- Runtime: OpenJDK/Temurin 17.0.20.1.
- Maven: 3.9.11 through the project wrapper.
- Default application database: H2 2.3.
- MySQL compatibility suite: H2 `MODE=MySQL` with the production `mysql` profile.
- Real MySQL: GitHub Actions `mysql:8.0` service.
- Payment: sandbox provider; no real funds or production payment credentials.
- Email: Mock transport by default; optional `smtp-email` profile; local loopback SMTP conversation verified with a real JavaMail exchange. No external SMTP provider was contacted.

## 2. Result summary

| Item | Result |
| --- | --- |
| Tests discovered | 338 |
| Passed | 333 |
| Failed | 0 |
| Errors | 0 |
| Skipped | 5 (`MySqlLiveIntegrationTest`, no local `MYSQL_URL`) |
| Build | BUILD SUCCESS |
| JaCoCo line coverage | 90.52% |
| JaCoCo branch coverage | 74.06% |
| Flyway migrations | V1–V8 applied to clean H2 and exercised by MySQL CI |
| Packaged artifact | `target/holiday-resort-booking-0.1.0-SNAPSHOT.jar` |
| Java 17 CI | PASS |
| MySQL 8 CI | PASS |
| SMTP loopback evidence | PASS |
| Q5/Q7 participant evidence | NOT COLLECTED |

The 90% bundle line rule is enforced by Maven. Skipped tests are environment-gated, not treated as passes.

## 3. Integrated functional scope

### Customer

- register, login, logout, role-aware navigation
- room search and server-side availability check
- server-calculated quote with active-member discount
- idempotent booking creation and room hold
- owned booking list/detail with separate booking and payment states
- cancellation, date/room/type change, re-pricing and inventory release
- sandbox booking payment and membership-fee payment
- ownership checks, CSRF, `no-store`, escaping and request-field allow lists

### STAFF

- room management and room-type base-rate maintenance
- real booking list/detail from Booking/Payment services
- reference, arrival-date and booking-state filters plus 20-row pagination
- allocated room IDs, price snapshot, booking state and payment state
- under-review payment confirmation/rejection only, with audited rejection reason
- no arbitrary amount, booking-state or allocation editing

### Persistence, notification and verification

- H2 and MySQL 8 Flyway schema, JDBC adapters and transactional inventory port
- unified lock ordering and `READ_COMMITTED` write boundaries
- booking-to-reminder create/change/cancel lifecycle
- retryable reminder outbox with stale-claim recovery
- SMTP loopback delivery test using a real SMTP command sequence
- JUnit, MockMvc, JaCoCo, packaged JAR and GitHub Actions

## 4. Requirement and evidence trace

| Requirement area | Result | Evidence |
| --- | --- | --- |
| FR-REM-001..003 | PASS | ReminderServiceTest, BookingReminderEventListenerTest, JdbcReminderRepositoryTest |
| FR-DB-001..004 | PASS for H2, H2-MySQL-mode and MySQL 8 CI | SchemaContractTest, MySqlProfileIntegrationTest, MySqlLiveIntegrationTest, CI |
| FR-BOOK-001..006 | PASS for implemented quote, allocation, cancellation and change paths | BookingServiceTest, JdbcBookingInventoryPort tests, CustomerStayWebTest, MySql profile/live tests |
| FR-PAY-001..005 | PASS with sandbox provider only | PaymentServiceTest, MembershipPaymentServiceTest, CustomerStayWebTest, MySql tests |
| FR-MEM-001..009 | PASS for registration, ownership, role and membership-payment integration | MemberAccountWebTest, MembershipPaymentServiceTest, CustomerStayWebTest |
| UI-STAY / FR-BOOK-004..006 | PASS in automated browser-contract tests | CustomerStayWebTest |
| UI-STAFF-STAY / FR-BOOK-005 | PASS for real STAFF routes, including under-review reconciliation | StaffStayAdminWebTest |
| NFR-SEC-01 / NFR-PRI-01 | PASS for tested paths | ownership tests, CSRF tests, no credential rendering, SMTP privacy assertions |
| Q5/Q7 user testing | PENDING | protocol and templates ready; no real participants yet |

Detailed D cases remain in `d-test-case-register.csv`; integrated customer/STAFF cases are in `CustomerStayWebTest` and `StaffStayAdminWebTest`.

## 5. Added after the internal D baseline

- `CustomerStayWebTest`: 9 end-to-end MockMvc scenarios covering quote, idempotency, cross-customer isolation, change/cancel, CSRF, payment return, failed/unknown payment and validation.
- `StaffStayAdminWebTest`: 8 scenarios covering STAFF-only access, filters, pagination, XSS escaping, audit reason, reconciliation and no-payment states.
- `SmtpLoopbackDeliveryTest`: real SMTP conversation against an ephemeral loopback test mailbox.
- customer and STAFF ownership hardening in Booking/Payment services.
- final integrated customer and STAFF routes while retaining isolated preview profiles.
- browser walkthrough on Edge found that the shared login username used `type="email"` and blocked non-email STAFF accounts; changed to `type="text"` and added a rendered-contract regression assertion.

## 6. CI evidence

Final integrated main commit before the final documentation tag:

- `22b06df` — merge of PR #10.
- Push workflow run [44019910978](https://github.com/muzaturezhang/SE/actions/runs/44019910978): Java 17 PASS; MySQL 8 PASS.
- Pull-request workflow run [44019929419](https://github.com/muzaturezhang/SE/actions/runs/44019929419): Java 17 PASS; MySQL 8 PASS.

Earlier integrated stages:

- PR #8 customer booking UI: runs [44018023452](https://github.com/muzaturezhang/SE/actions/runs/44018023452) and [44018044384](https://github.com/muzaturezhang/SE/actions/runs/44018044384) PASS.
- PR #9 STAFF booking administration: runs [44019392648](https://github.com/muzaturezhang/SE/actions/runs/44019392648) and [44019409062](https://github.com/muzaturezhang/SE/actions/runs/44019409062) PASS.

## 7. Known limitations

1. Q5/Q7 user testing is not collected. Real participants, consent, task observation, anonymisation and defect retests are still required.
2. External SMTP/Mailtrap delivery is not verified. Only the Mock transport, failure conversion and local loopback SMTP exchange are evidenced.
3. Payment is sandbox-only; no real provider settlement, refund integration or chargeback evidence exists.
4. Final-build desktop/mobile screenshots are captured under `docs/screenshots/final-2026-11-14/`; selecting and embedding them in the final report/PPT remains outstanding.
5. No claim is made of production-scale load, disaster recovery, or operations monitoring beyond the implemented health endpoint and bounded reminder lease.
6. Final report/PPT and the final course tag must reference the same build as the user-test evidence.

## 8. Reproduction

```powershell
cd "C:\Users\Lenovo\Documents\ChatGPT\软件工程\SE"
.\scripts\test.ps1
```

Targeted SMTP evidence:

```powershell
$env:JAVA_HOME = "C:\Users\Lenovo\Documents\ChatGPT\软件工程\SE\.cache\jdk17\extracted\jdk-17.0.20.1+1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\scripts\maven.ps1 --batch-mode --no-transfer-progress "-Dtest=SmtpLoopbackDeliveryTest" test
```

## 9. Conclusion

The integrated repository has a verified engineering baseline for customer booking, STAFF administration, persistence, payment sandbox, reminders, SMTP protocol delivery and CI. A 14-page Q1–Q8 report, PDF and 12-slide deck are prepared under `deliverables/final/`. It is not yet honest to call the full course deliverable complete: Q5/Q7 evidence, external email evidence, official group metadata and a final same-build tag remain outstanding.