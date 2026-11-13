-- Align the pending D foundation with C's frozen booking/payment contracts
-- without rewriting migrations that may already have been applied.
--
-- Booking idempotency is mandatory for retry safety. Existing rows are
-- permitted to have NULL only for pre-release compatibility.
ALTER TABLE booking ADD COLUMN idempotency_key VARCHAR(80);
ALTER TABLE booking ADD COLUMN discount_percent INTEGER;

ALTER TABLE booking ADD CONSTRAINT uk_booking_idempotency UNIQUE (idempotency_key);
ALTER TABLE booking ADD CONSTRAINT ck_booking_discount_percent CHECK (
    discount_percent IS NULL OR discount_percent BETWEEN 0 AND 99
);

ALTER TABLE payment ADD COLUMN failure_reason VARCHAR(500);

-- Active room-night rows are a projection used to enforce overlap. History is
-- append-only and is written before the projection row is removed on release.
CREATE TABLE booking_room_night_history (
    booking_reference VARCHAR(40) NOT NULL,
    room_id VARCHAR(20) NOT NULL,
    stay_date DATE NOT NULL,
    released_at TIMESTAMP(6) NOT NULL,
    release_reason VARCHAR(64) NOT NULL,
    PRIMARY KEY (booking_reference, room_id, stay_date),
    CONSTRAINT fk_night_history_allocation FOREIGN KEY (booking_reference, room_id)
        REFERENCES booking_room_allocation (booking_reference, room_id)
);

CREATE INDEX idx_night_history_room_date
    ON booking_room_night_history (room_id, stay_date);

-- Append-only booking state audit; no booking rows are physically deleted.
CREATE TABLE booking_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_reference VARCHAR(40) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    previous_state VARCHAR(32),
    new_state VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_booking_event_booking FOREIGN KEY (booking_reference)
        REFERENCES booking (reference)
);

CREATE INDEX idx_booking_event_reference_time
    ON booking_event (booking_reference, occurred_at);

-- Membership periods and payments are audited separately from the current
-- entitlement row so renewals do not erase the previous period's evidence.
CREATE TABLE membership_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    previous_payment_confirmed BOOLEAN,
    new_payment_confirmed BOOLEAN,
    previous_valid_from DATE,
    previous_valid_until DATE,
    new_valid_from DATE,
    new_valid_until DATE,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_membership_event_customer FOREIGN KEY (customer_id)
        REFERENCES customer (id)
);

CREATE INDEX idx_membership_event_customer_time
    ON membership_event (customer_id, occurred_at);
