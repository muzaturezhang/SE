# 数据库迁移

D owns schema migrations through Flyway. Production-like schema changes must be immutable
versioned SQL files reviewed with the affected module owners.

`V1__create_reminder_schedule.sql` is the first D-owned foundation migration. It stores
idempotent one-day reminder schedules and dispatch state. It does not define Booking, Payment,
Room, Member, or shared status tables; those schemas still require cross-module review.

Local/dev/test and MySQL use the same migration path. Do not edit an applied migration in a
shared branch; add a new version and document the compatibility or migration impact.
