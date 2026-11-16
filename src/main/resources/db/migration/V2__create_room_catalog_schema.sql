-- D-owned room catalogue, inventory history and occupancy storage.
-- All nullable timestamps use UTC instants; business dates use LocalDate.

CREATE TABLE room_type (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    english_name VARCHAR(80) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    image_path VARCHAR(255) NOT NULL,
    image_alt VARCHAR(255) NOT NULL,
    features_json VARCHAR(2000) NOT NULL,
    nightly_rate_minor BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_room_type_rate_positive CHECK (nightly_rate_minor > 0),
    CONSTRAINT ck_room_type_version_non_negative CHECK (version >= 0)
);

CREATE TABLE room (
    id VARCHAR(20) PRIMARY KEY,
    room_type_id VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_room_room_type FOREIGN KEY (room_type_id) REFERENCES room_type (id),
    CONSTRAINT ck_room_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_room_type_enabled ON room (room_type_id, enabled);

CREATE TABLE room_block (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id VARCHAR(20) NOT NULL,
    arrival_date DATE NOT NULL,
    departure_date DATE NOT NULL,
    reason VARCHAR(255) NOT NULL,
    source_reference VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_room_block_room FOREIGN KEY (room_id) REFERENCES room (id),
    CONSTRAINT ck_room_block_period CHECK (departure_date > arrival_date)
);

CREATE INDEX idx_room_block_overlap
    ON room_block (room_id, arrival_date, departure_date);

CREATE TABLE room_change_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id VARCHAR(20) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    previous_room_type_id VARCHAR(32),
    new_room_type_id VARCHAR(32),
    previous_enabled BOOLEAN,
    new_enabled BOOLEAN,
    changed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_room_change_room FOREIGN KEY (room_id) REFERENCES room (id)
);

CREATE INDEX idx_room_change_room_time ON room_change_event (room_id, changed_at);

CREATE TABLE room_type_rate_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_type_id VARCHAR(32) NOT NULL,
    previous_rate_minor BIGINT,
    new_rate_minor BIGINT NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_rate_event_room_type FOREIGN KEY (room_type_id) REFERENCES room_type (id),
    CONSTRAINT ck_rate_event_new_positive CHECK (new_rate_minor > 0)
);

CREATE INDEX idx_rate_event_type_time ON room_type_rate_event (room_type_id, changed_at);