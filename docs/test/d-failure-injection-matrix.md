# D Failure-Injection and Boundary Matrix

Status: internal engineering trace. `PASS` means an automated test or successful CI job exists; it does not turn a prototype limitation into a production claim.

| Scenario | Expected behaviour | Evidence | Status |
| --- | --- | --- | --- |
| Duplicate booking submit with same idempotency key | Return the original booking; do not allocate twice | `MySqlProfileIntegrationTest` | PASS |
| No rooms left for requested stay | Return a conflict and persist no partial booking | `BookingServiceTest`, `MySqlProfileIntegrationTest` | PASS |
| Released booking inventory | Preserve release history and allow the room to be sold again | `MySqlProfileIntegrationTest.releasedInventoryCanBeSoldAgainWhileHistoryRemains` | PASS |
| Reminder provider failure | Retry with backoff, then fail after max attempts | `ReminderServiceTest`, `JdbcReminderRepositoryTest` | PASS |
| Reminder process crash in `SENDING` | Reclaim stale lease or fail when attempts are exhausted | `JdbcReminderRepositoryTest` | PASS |
| Same-day arrival cannot receive one-day reminder | Skip reminder without failing the booking | `BookingReminderEventListenerTest` | PASS |
| Non-date notification error | Propagate the error instead of silently swallowing it | `BookingReminderEventListenerTest` | PASS |
| Duplicate payment callback | Keep one payment outcome and do not double-charge/update twice | `MySqlProfileIntegrationTest`, `PaymentServiceTest` | PASS |
| Staff reconciliation on a non-under-review payment | Reject the operation as a conflict | `PaymentServiceTest.staffCannotConfirmPendingPayment`, `staffCannotRejectPaidPayment` | PASS |
| Provider query timeout/unknown | Move payment and booking to `UNDER_REVIEW`; keep inventory | `PaymentServiceTest`, `MySqlProfileIntegrationTest` | PASS |
| MySQL migration on real server | Apply V1–V8 and seed without failure | GitHub Actions run `43881256201` / `43882193364` | PASS |
| Real SMTP provider, bounce and quota | Provider-specific delivery evidence | None yet | NOT TESTED |
| Cancellation/date-change event integration | Reschedule/cancel reminder through C's real event path | Listener unit/integration coverage exists; C service path incomplete | PARTIAL — blocked by C scope |
| Membership fee payment | Separate payment, then idempotent `confirmPayment` | Schema/adapter only | NOT IMPLEMENTED IN C |
