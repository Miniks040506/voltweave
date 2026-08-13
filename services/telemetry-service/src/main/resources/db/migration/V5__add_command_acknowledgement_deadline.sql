ALTER TABLE command_deliveries
    ADD COLUMN acknowledgement_deadline_at TIMESTAMPTZ;

UPDATE command_deliveries
SET acknowledgement_deadline_at = LEAST(valid_from + interval '30 seconds', expires_at);

ALTER TABLE command_deliveries
    ALTER COLUMN acknowledgement_deadline_at SET NOT NULL,
    ADD CONSTRAINT command_deliveries_ack_deadline_check CHECK (
        acknowledgement_deadline_at >= valid_from
        AND acknowledgement_deadline_at <= expires_at
    );

DROP INDEX command_deliveries_ready_idx;
CREATE INDEX command_deliveries_ready_idx
    ON command_deliveries (next_attempt_at, acknowledgement_deadline_at)
    WHERE status IN ('PENDING', 'PUBLISHED');
