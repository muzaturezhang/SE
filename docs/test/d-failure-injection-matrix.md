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
| MySQL migration and persistence lifecycle on real server | Apply V1–V8, seed, and exercise booking/payment/reminder/release persistence | GitHub Actions MySQL 8 jobs | PASS |
| Concurrent bookings competing for limited rooms on real MySQL | Exactly one booking succeeds and no partial over-allocation is persisted | `MySqlLiveIntegrationTest.concurrentBookingCompetitionAllowsExactlyOneWinner`; CI runs 43892676670 / 43892690685 | PASS |
| Real SMTP provider, bounce and quota | Provider-specific delivery evidence | None yet | NOT TESTED |
| Cancellation/date-change event integration | Release/reallocate inventory and cancel/reschedule the reminder | `BookingServiceTest`, `MySqlProfileIntegrationTest`, `MySqlLiveIntegrationTest` | PASS |
| Membership fee payment | Separate payment, then idempotent `confirmPayment` | MembershipPaymentServiceTest / MySqlProfileIntegrationTest | PASS |
