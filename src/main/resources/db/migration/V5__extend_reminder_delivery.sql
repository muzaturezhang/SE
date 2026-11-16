-- Extend the existing reminder schedule without mutating the applied V1 checksum.
-- The legacy name is retained as the implementation of the ERD's reminder table.

ALTER TABLE reminder_schedule ADD COLUMN check_out_date DATE;
ALTER TABLE reminder_schedule ADD COLUMN room_type_name VARCHAR(80);
ALTER TABLE reminder_schedule ADD COLUMN scheduled_at TIMESTAMP(6);

CREATE INDEX idx_reminder_scheduled
    ON reminder_schedule (status, scheduled_at, next_attempt_at);

-- Audit history intentionally excludes recipient email and other guest personal data.
CREATE TABLE reminder_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reminder_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    previous_check_in_date DATE,
    previous_check_out_date DATE,
    previous_room_type_name VARCHAR(80),
    previous_scheduled_at TIMESTAMP(6),
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_reminder_event_reminder FOREIGN KEY (reminder_id)
        REFERENCES reminder_schedule (id)
);

CREATE INDEX idx_reminder_event_reminder_time ON reminder_event (reminder_id, occurred_at);