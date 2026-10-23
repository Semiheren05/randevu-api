-- One-off closed days: a public holiday or vacation for the whole business (staff_id NULL),
-- or a day off for one staff member. Both ends are inclusive: 2026-12-31 to 2027-01-01 is two days.
CREATE TABLE closures (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_id BIGINT       NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    staff_id    BIGINT       REFERENCES staff (id) ON DELETE CASCADE,
    starts_on   DATE         NOT NULL,
    ends_on     DATE         NOT NULL,
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_closures_range CHECK (starts_on <= ends_on)
);

CREATE INDEX ix_closures_business_dates ON closures (business_id, starts_on, ends_on);
