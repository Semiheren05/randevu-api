-- Needed so the exclusion constraint below can combine "=" on business_id with "&&" on time ranges
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE businesses (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id    BIGINT        NOT NULL REFERENCES users (id),
    slug        VARCHAR(120)  NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(1000),
    city        VARCHAR(80),
    time_zone   VARCHAR(60)   NOT NULL DEFAULT 'Europe/Istanbul',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_businesses_slug UNIQUE (slug)
);

CREATE INDEX ix_businesses_owner ON businesses (owner_id);
CREATE INDEX ix_businesses_city ON businesses (lower(city));

CREATE TABLE business_hours (
    business_id BIGINT   NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL,
    opens_at    TIME     NOT NULL,
    closes_at   TIME     NOT NULL,
    PRIMARY KEY (business_id, day_of_week),
    CONSTRAINT ck_business_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_business_hours_range CHECK (opens_at < closes_at)
);

CREATE TABLE offerings (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id      BIGINT        NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    name             VARCHAR(120)  NOT NULL,
    duration_minutes INTEGER       NOT NULL,
    price            NUMERIC(10, 2) NOT NULL,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_offerings_duration CHECK (duration_minutes BETWEEN 5 AND 480),
    CONSTRAINT ck_offerings_price CHECK (price >= 0)
);

CREATE INDEX ix_offerings_business ON offerings (business_id);

CREATE TABLE bookings (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id  BIGINT         NOT NULL REFERENCES businesses (id),
    offering_id  BIGINT         NOT NULL REFERENCES offerings (id),
    customer_id  BIGINT         NOT NULL REFERENCES users (id),
    starts_at    TIMESTAMPTZ    NOT NULL,
    ends_at      TIMESTAMPTZ    NOT NULL,
    price        NUMERIC(10, 2) NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    note         VARCHAR(500),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT ck_bookings_range CHECK (starts_at < ends_at),
    CONSTRAINT ck_bookings_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_bookings_cancelled_at CHECK (status = 'CONFIRMED' OR cancelled_at IS NOT NULL),
    -- Two confirmed bookings of the same business can never overlap, even when two
    -- requests arrive at the same moment. '[)' means the end is exclusive, so a 10:00-10:30
    -- booking and a 10:30-11:00 booking do not overlap.
    CONSTRAINT ex_bookings_no_overlap EXCLUDE USING gist (
        business_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status = 'CONFIRMED')
);

CREATE INDEX ix_bookings_customer ON bookings (customer_id, starts_at DESC);
CREATE INDEX ix_bookings_business_start ON bookings (business_id, starts_at);
