-- Initial resort catalogue used by the MySQL profile. Runtime changes are audited and
-- retained by the persistence adapters; this migration is intentionally immutable.

INSERT INTO room_type (
    id, name, english_name, description, image_path, image_alt, features_json,
    nightly_rate_minor, created_at, updated_at, version
) VALUES
    ('courtyard', '庭院客房', 'COURTYARD ROOM',
     '温暖的木色、柔软的织物与自然光。适合两人短住的安静空间。',
     '/images/suite-interior.jpg', '暖色木饰与白色床品的庭院客房展示',
     '["庭院景观","双人入住"]', 98000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('terrace', '露台套房', 'TERRACE SUITE',
     '将露台作为起居的一部分。留出阅读、休息，以及与同行人相处的空间。',
     '/images/coastal-retreat.jpg', '露台与热带绿植环绕的度假居所展示',
     '["户外露台","独立起居空间"]', 168000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('pool', '泳池别墅', 'POOL VILLA',
     '泳池、露台与室内居所相连，为长一些的假期留出充裕空间。',
     '/images/tropical-pool.jpg', '泳池与棕榈树相邻的度假别墅展示',
     '["泳池空间","户外休憩区"]', 328000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO room (id, room_type_id, enabled, created_at, updated_at, version) VALUES
    ('C101', 'courtyard', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('C102', 'courtyard', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('C103', 'courtyard', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('C104', 'courtyard', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('C105', 'courtyard', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('T201', 'terrace', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('T202', 'terrace', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('P301', 'pool', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);