-- D-owned customer, staff identity and membership persistence.
-- email_normalized is the case-insensitive uniqueness key; application code must lower-case it.

CREATE TABLE customer (
    id VARCHAR(36) PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL,
    email_normalized VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    registered_on DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_customer_email_normalized UNIQUE (email_normalized),
    CONSTRAINT ck_customer_email_normalized_lower CHECK (email_normalized = LOWER(email_normalized)),
    CONSTRAINT ck_customer_version_non_negative CHECK (version >= 0)
);

CREATE TABLE staff_account (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_staff_username UNIQUE (username),
    CONSTRAINT ck_staff_version_non_negative CHECK (version >= 0)
);

CREATE TABLE membership (
    customer_id VARCHAR(36) PRIMARY KEY,
    payment_confirmed BOOLEAN NOT NULL,
    fee_minor BIGINT NOT NULL,
    discount_basis_points INTEGER NOT NULL,
    valid_from DATE,
    valid_until DATE,
    payment_reference VARCHAR(128),
    confirmed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_membership_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT ck_membership_fee_positive CHECK (fee_minor > 0),
    CONSTRAINT ck_membership_discount_range CHECK (discount_basis_points BETWEEN 0 AND 10000),
    CONSTRAINT ck_membership_validity CHECK (
        valid_from IS NULL OR valid_until IS NULL OR valid_until >= valid_from
    ),
    CONSTRAINT ck_membership_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_membership_validity ON membership (payment_confirmed, valid_from, valid_until);