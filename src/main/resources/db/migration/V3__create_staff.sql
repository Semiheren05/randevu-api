-- Staff members: a business with staff takes one booking per person at a time,
-- so two hairdressers can serve two customers at 14:00.
CREATE TABLE staff (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id BIGINT      NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    name        VARCHAR(80) NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_staff_business ON staff (business_id);

-- Which services a staff member can do. No rows means: every service of the business.
CREATE TABLE staff_offerings (
    staff_id    BIGINT NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    offering_id BIGINT NOT NULL REFERENCES offerings (id) ON DELETE CASCADE,
    PRIMARY KEY (staff_id, offering_id)
);

ALTER TABLE bookings ADD COLUMN staff_id BIGINT REFERENCES staff (id);

-- The overlap rule now depends on who does the work:
--   * bookings without staff (a business with no staff) must not overlap within the business
--   * bookings with staff must not overlap for the same staff member
ALTER TABLE bookings DROP CONSTRAINT ex_bookings_no_overlap;

ALTER TABLE bookings ADD CONSTRAINT ex_bookings_no_overlap_business EXCLUDE USING gist (
    business_id WITH =,
    tstzrange(starts_at, ends_at, '[)') WITH &&
) WHERE (status = 'CONFIRMED' AND staff_id IS NULL);

ALTER TABLE bookings ADD CONSTRAINT ex_bookings_no_overlap_staff EXCLUDE USING gist (
    staff_id WITH =,
    tstzrange(starts_at, ends_at, '[)') WITH &&
) WHERE (status = 'CONFIRMED' AND staff_id IS NOT NULL);

CREATE INDEX ix_bookings_staff_start ON bookings (staff_id, starts_at);
