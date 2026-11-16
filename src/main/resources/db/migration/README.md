# 数据库迁移

D owns schema migrations through Flyway. Production-like schema changes must be immutable
versioned SQL files reviewed with the affected module owners.

| Version | Scope |
| --- | --- |
| `V1` | Idempotent reminder schedule and dispatch state |
| `V2` | Room types, rooms, manual blocks and room/rate audit |
| `V3` | Customers, staff accounts and memberships |
| `V4` | Bookings, room allocations/nights, payments and payment events |
| `V5` | Reminder departure/room-type/send-time fields and reminder audit |
| `V6` | Portable named locks for the shared transaction order |
| `V7` | Initial room catalogue for the persistent application |

The same migrations run on H2 for dev/test and MySQL 8 for the `mysql` profile. Do not edit an
applied migration in a shared branch; add a new version and document compatibility impact.

Schema invariants:

- Room number is the `room` primary key.
- `customer.email_normalized` is unique and must be lower-case.
- `booking_room_night(room_id, stay_date)` prevents two active bookings from taking the same room-night.
- Payment idempotency keys are unique.
- Monetary columns are integer CNY fen.
- State changes are retained through audit tables; module code must not issue physical deletes.