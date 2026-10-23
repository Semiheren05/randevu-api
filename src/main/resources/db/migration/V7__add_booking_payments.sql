-- Online payment of a booking. Paying is optional: a booking is confirmed either way and
-- UNPAID means the customer pays at the business.
ALTER TABLE bookings
    ADD COLUMN payment_status      VARCHAR(20)  NOT NULL DEFAULT 'UNPAID',
    ADD COLUMN payment_provider    VARCHAR(20),
    ADD COLUMN payment_checkout_id VARCHAR(255),
    ADD COLUMN payment_reference   VARCHAR(255),
    ADD COLUMN paid_at             TIMESTAMPTZ,
    ADD COLUMN refunded_at         TIMESTAMPTZ,
    ADD CONSTRAINT ck_bookings_payment_status CHECK (payment_status IN ('UNPAID', 'PENDING', 'PAID', 'REFUNDED')),
    ADD CONSTRAINT uk_bookings_payment_checkout UNIQUE (payment_checkout_id);
