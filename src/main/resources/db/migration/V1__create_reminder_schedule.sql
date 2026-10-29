CREATE TABLE reminder_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_reference VARCHAR(64) NOT NULL,
    recipient_email VARCHAR(254) NOT NULL,
    check_in_date DATE NOT NULL,
    remind_on_date DATE NOT NULL,
    reminder_kind VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NULL,
    last_attempt_at TIMESTAMP(6) NULL,
    sent_at TIMESTAMP(6) NULL,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_reminder_booking_kind UNIQUE (booking_reference, reminder_kind),
    CONSTRAINT ck_reminder_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_reminder_version CHECK (version >= 0)
);

CREATE INDEX idx_reminder_due
    ON reminder_schedule (status, remind_on_date, next_attempt_at);
