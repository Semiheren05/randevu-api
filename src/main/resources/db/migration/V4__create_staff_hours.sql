-- A staff member's own weekly hours, e.g. a part-timer who works Monday to Wednesday afternoons.
-- No rows means the person follows the business's opening hours.
-- Either way only the part inside the business's opening hours can be booked.
CREATE TABLE staff_hours (
    staff_id    BIGINT   NOT NULL REFERENCES staff (id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL,
    opens_at    TIME     NOT NULL,
    closes_at   TIME     NOT NULL,
    PRIMARY KEY (staff_id, day_of_week),
    CONSTRAINT ck_staff_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_staff_hours_range CHECK (opens_at < closes_at)
);
