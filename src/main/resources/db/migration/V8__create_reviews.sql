-- A customer's rating (1-5 stars) and optional comment about a booking that took place.
-- One review per booking, so the average cannot be pushed up by reviewing the same visit again.
CREATE TABLE reviews (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id  BIGINT        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    business_id BIGINT        NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    customer_id BIGINT        NOT NULL REFERENCES users (id),
    rating      INTEGER       NOT NULL,
    comment     VARCHAR(1000),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_reviews_booking UNIQUE (booking_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX ix_reviews_business_created ON reviews (business_id, created_at DESC);
