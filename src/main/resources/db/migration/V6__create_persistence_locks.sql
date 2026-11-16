-- A small, portable lock table gives MySQL and H2 the same lock ordering.
-- Callers must hold a transaction and lock rows in canonical order:
-- room inventory -> booking -> payment.

CREATE TABLE persistence_lock (
    id VARCHAR(32) PRIMARY KEY,
    description VARCHAR(120) NOT NULL
);

INSERT INTO persistence_lock (id, description) VALUES
    ('ROOM_INVENTORY', 'Serializes room catalogue and physical-room changes'),
    ('BOOKING', 'Serializes booking creation and lifecycle changes'),
    ('PAYMENT', 'Serializes payment creation and reconciliation changes');