-- Clean mutable test data without deleting the migration-owned room catalogue.
-- H2 uses a fresh schema per test JVM; this script also supports repeatable integration fixtures.

DELETE FROM reminder_event;
DELETE FROM membership_event;
DELETE FROM booking_event;
DELETE FROM booking_room_night_history;
DELETE FROM reminder_schedule;
DELETE FROM payment_event;
DELETE FROM payment;
DELETE FROM booking_room_night;
DELETE FROM booking_room_allocation;
DELETE FROM booking;
DELETE FROM membership;
DELETE FROM staff_account;
DELETE FROM customer;
DELETE FROM room_block;
DELETE FROM room_change_event;
DELETE FROM room_type_rate_event;
