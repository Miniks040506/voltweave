ALTER TABLE device_commands
    ADD COLUMN acknowledgement_deadline_at TIMESTAMPTZ;

UPDATE device_commands
SET acknowledgement_deadline_at = LEAST(valid_from + interval '30 seconds', expires_at);

ALTER TABLE device_commands
    ALTER COLUMN acknowledgement_deadline_at SET NOT NULL,
    ADD CONSTRAINT device_commands_ack_deadline_check CHECK (
        acknowledgement_deadline_at >= valid_from
        AND acknowledgement_deadline_at <= expires_at
    );

CREATE INDEX device_commands_ack_deadline_idx
    ON device_commands (acknowledgement_deadline_at)
    WHERE status = 'REQUESTED';
