-- D-owned booking and payment persistence. State strings remain owned by C until the
-- booking/payment contract review freezes their exact allowed values.

CREATE TABLE booking (
    reference VARCHAR(40) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    room_type_id VARCHAR(32) NOT NULL,
    guest_name VARCHAR(80) NOT NULL,
    guest_email VARCHAR(254) NOT NULL,
    arrival_date DATE NOT NULL,
    departure_date DATE NOT NULL,
    room_count INTEGER NOT NULL,
    base_rate_minor BIGINT NOT NULL,
    nights INTEGER NOT NULL,
    subtotal_minor BIGINT NOT NULL,
    discount_minor BIGINT NOT NULL,
    discount_label VARCHAR(80),
    total_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    state VARCHAR(32) NOT NULL,
    payment_deadline TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_booking_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_booking_room_type FOREIGN KEY (room_type_id) REFERENCES room_type (id),
    CONSTRAINT ck_booking_period CHECK (departure_date > arrival_date),
    CONSTRAINT ck_booking_room_count_positive CHECK (room_count > 0),
    CONSTRAINT ck_booking_nights_positive CHECK (nights > 0),
    CONSTRAINT ck_booking_rate_positive CHECK (base_rate_minor > 0),
    CONSTRAINT ck_booking_amounts CHECK (
        subtotal_minor >= 0 AND discount_minor >= 0 AND discount_minor <= subtotal_minor
        AND total_minor >= 0
    ),
    CONSTRAINT ck_booking_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_booking_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_booking_customer_created ON booking (customer_id, created_at);
CREATE INDEX idx_booking_arrival_state ON booking (arrival_date, state);

CREATE TABLE booking_room_allocation (
    booking_reference VARCHAR(40) NOT NULL,
    room_id VARCHAR(20) NOT NULL,
    allocated_at TIMESTAMP(6) NOT NULL,
    released_at TIMESTAMP(6),
    release_reason VARCHAR(64),
    PRIMARY KEY (booking_reference, room_id),
    CONSTRAINT fk_allocation_booking FOREIGN KEY (booking_reference) REFERENCES booking (reference),
    CONSTRAINT fk_allocation_room FOREIGN KEY (room_id) REFERENCES room (id)
);

CREATE INDEX idx_allocation_room_release ON booking_room_allocation (room_id, released_at);

-- One row per allocated room-night makes overlapping active allocations impossible in both
-- MySQL and H2 without vendor-specific exclusion constraints.
CREATE TABLE booking_room_night (
    room_id VARCHAR(20) NOT NULL,
    stay_date DATE NOT NULL,
    booking_reference VARCHAR(40) NOT NULL,
    PRIMARY KEY (room_id, stay_date),
    CONSTRAINT fk_booking_night_allocation FOREIGN KEY (booking_reference, room_id)
        REFERENCES booking_room_allocation (booking_reference, room_id)
);

CREATE INDEX idx_booking_night_booking ON booking_room_night (booking_reference);

CREATE TABLE payment (
    id VARCHAR(36) PRIMARY KEY,
    purpose VARCHAR(16) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    booking_reference VARCHAR(40),
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    state VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128),
    idempotency_key VARCHAR(80) NOT NULL,
    expires_at TIMESTAMP(6),
    paid_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_reference) REFERENCES booking (reference),
    CONSTRAINT uk_payment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_payment_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_payment_currency CHECK (currency = 'CNY'),
    CONSTRAINT ck_payment_purpose CHECK (
        (purpose = 'BOOKING' AND booking_reference IS NOT NULL)
        OR (purpose = 'MEMBERSHIP' AND booking_reference IS NULL)
    ),
    CONSTRAINT ck_payment_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_payment_booking ON payment (booking_reference);
CREATE INDEX idx_payment_customer_created ON payment (customer_id, created_at);
CREATE INDEX idx_payment_state_expiry ON payment (state, expires_at);

CREATE TABLE payment_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    provider_event_id VARCHAR(128),
    state_from VARCHAR(32),
    state_to VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_payment_event_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT uk_payment_provider_event UNIQUE (provider_event_id)
);

CREATE INDEX idx_payment_event_payment_time ON payment_event (payment_id, occurred_at);