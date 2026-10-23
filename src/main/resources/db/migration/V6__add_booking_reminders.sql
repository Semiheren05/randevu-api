-- When the "your appointment is coming up" email went out; NULL means not yet.
ALTER TABLE bookings ADD COLUMN reminder_sent_at TIMESTAMPTZ;

-- The reminder job looks for confirmed, not yet reminded bookings that start soon
CREATE INDEX ix_bookings_reminder_due ON bookings (starts_at)
    WHERE status = 'CONFIRMED' AND reminder_sent_at IS NULL;
