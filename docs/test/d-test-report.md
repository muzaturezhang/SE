# D Test Report — 2026-11-13

Status: internal engineering verification. This is not a final course Test Report, Q5/Q7 user-test result, or production acceptance record.

## 1. Environment

- Execution host: Windows, PowerShell.
- Runtime: Temurin/OpenJDK 17.0.20.1.
- Maven: 3.9.11 via project wrapper.
- Default application database: H2 2.3.
- MySQL-mode integration: H2 `MODE=MySQL` with the production profile selected.
- Live MySQL test: verified by GitHub Actions MySQL 8 jobs; locally gated by `MYSQL_URL`.
- Email: Mock transport; SMTP adapter unit-tested with a mocked JavaMailSender.
- Payment: sandbox provider; no real funds moved.

## 2. Result summary

| Item | Result |
| --- | --- |
| Tests executed | 305 |
| Passed | 302 |
| Failed | 0 |
| Errors | 0 |
| Skipped | 3 (`MySqlLiveIntegrationTest`, no local `MYSQL_URL`) |
| Build | BUILD SUCCESS |
| JaCoCo line coverage | 90.89% |
| JaCoCo branch coverage | 75.59% |
| Flyway migrations | 8 applied successfully to a clean H2 schema |
| Packaged artifact | `target/holiday-resort-booking-0.1.0-SNAPSHOT.jar` |
| CI Java 17 verification | PASS |
| CI MySQL 8 integration | PASS |

The coverage rule is enforcing a 90% bundle line threshold. Branch coverage is reported separately because the remaining uncovered branches are concentrated in environment-specific staff provisioning, provider failure variants and live MySQL gating.

## 3. D requirements

| Requirement | Result | Evidence |
| --- | --- | --- |
| FR-REM-001 | PASS | ReminderServiceTest / JdbcReminderRepositoryTest |
| FR-REM-002 | PASS | ReminderServiceTest / SmtpEmailTransportTest |
| FR-REM-003 | PASS for create/paid/fail/expire wired paths; cancellation/change domain methods are not present in the current C UI | BookingReminderEventListener / MySqlProfileIntegrationTest |
| FR-DB-001 | PASS for H2, H2-MySQL-mode and real MySQL 8 CI | MySqlProfileIntegrationTest / MySqlLiveIntegrationTest / GitHub Actions |
| FR-DB-002 | PASS for tested constraints, idempotency and release history | SchemaContractTest / MySqlProfileIntegrationTest |
| FR-DB-003 | PASS for booking/payment paths covered by this slice | JdbcBookingInventoryPort / PaymentService / JdbcPersistenceLockService tests |
| FR-DB-004 | PASS locally and in GitHub Actions push/PR workflows | Maven verify / CI workflow |

## 4. Tests added or strengthened

- `BookingStatusTest`: terminal-state contract.
- `BookingReminderEventListenerTest`: created/paid/failed reminder actions, same-day skip and non-date error propagation.
- `ReminderDispatchLeaseTest`: dispatch invokes stale `SENDING` lease recovery.
- `JdbcStaffProvisionerConfigurationTest`: blank/partial/valid/idempotent staff provisioning.
- `MySqlProfileIntegrationTest`: real Spring wiring under the `mysql` profile with H2 MySQL mode, JDBC customer/membership/staff/booking/payment adapters, idempotent booking/payment submission, duplicate callback handling, booking/reminder lifecycle and released-room resale history.
- `MySqlLiveIntegrationTest`: real MySQL migration and persistence lifecycle test, environment-gated.
- `JdbcReminderRepositoryTest`: stale `SENDING` lease recovery.
- `docs/test/user-test-plan.md`, `user-test-consent-and-privacy.md`, `user-test-observation-sheet.csv`: ready-to-execute Q5/Q7 protocol; no participant results fabricated.
- `docs/test/d-failure-injection-matrix.md`: failure and boundary trace.
- `SchemaContractTest`: V8 history and audit tables.
- `BookingService`: dedicated `BookingInventoryPort` with in-memory and JDBC implementations.
- `PaymentService` / `PaymentTransactionService`: provider I/O outside the transaction, atomic payment+booking state updates, safer retry/idempotency handling, negative staff reconciliation cases and clock-based paid timestamps.
- Cross-module write transactions use `READ_COMMITTED` so lock acquisition is followed by a fresh availability read; the real-MySQL concurrency test caught and verified this boundary.

## 5. Known limitations

1. The local machine has no MySQL 8 server; the real MySQL test ran successfully in both GitHub Actions workflows.
2. No live SMTP provider was contacted; only adapter mapping/error conversion and the safe Mock transport were verified.
3. C's current UI still exposes preview routes and does not yet have production controllers for every C service operation.
4. Booking cancellation and date-change domain operations are still not present in C's current service surface. The reminder listener supports rescheduling/cancellation when those events are implemented.
5. Provider-level idempotency and delivery receipt/bounce processing are not available with the current sandbox/Mock provider.
6. User testing, screenshots and Q5/Q7 evidence require real participants and must not be inferred from this report.

## 6. CI evidence

- Push run [43890784432](https://github.com/muzaturezhang/SE/actions/runs/43890784432): Java 17 verification PASS; MySQL 8 integration PASS; 0 annotations.
- PR run [43890798856](https://github.com/muzaturezhang/SE/actions/runs/43890798856): Java 17 verification PASS; MySQL 8 integration PASS; 0 annotations.
- Real MySQL job now exercises booking creation/idempotency, payment callback/idempotency, reminder creation/cancellation and release history, not only migrations.

## 7. Conclusion

## 6. Conclusion

D's persistence and reminder foundation is wired through the merged A/B/C code, and the real MySQL 8 CI check is green. The remaining external/scope items are live SMTP testing, C's cancellation/change implementation, membership-fee payment orchestration and Q5/Q7 user testing.
