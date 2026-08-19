ALTER TABLE command_deliveries
    DROP CONSTRAINT command_deliveries_status_check,
    DROP CONSTRAINT command_deliveries_state_check;

ALTER TABLE command_deliveries
    ADD CONSTRAINT command_deliveries_status_check CHECK (
        status IN ('PENDING', 'PUBLISHED', 'ACKNOWLEDGED', 'TIMED_OUT')
    ),
    ADD CONSTRAINT command_deliveries_state_check CHECK (
        (status = 'PENDING' AND published_at IS NULL AND acknowledged_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL AND acknowledged_at IS NULL)
        OR (status = 'ACKNOWLEDGED' AND acknowledged_at IS NOT NULL)
        OR (status = 'TIMED_OUT' AND acknowledged_at IS NULL)
    );
