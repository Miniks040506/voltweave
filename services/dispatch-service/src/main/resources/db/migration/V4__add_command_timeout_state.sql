ALTER TABLE device_commands DROP CONSTRAINT device_commands_status_check;
ALTER TABLE device_commands DROP CONSTRAINT device_commands_ack_check;

ALTER TABLE device_commands
    ADD CONSTRAINT device_commands_status_check CHECK (
        status IN ('REQUESTED', 'ACCEPTED', 'REJECTED', 'TIMED_OUT')
    ),
    ADD CONSTRAINT device_commands_ack_check CHECK (
        (status = 'REQUESTED'
            AND applied_power_kw IS NULL
            AND rejection_reason IS NULL
            AND acknowledged_at IS NULL)
        OR
        (status = 'ACCEPTED'
            AND applied_power_kw IS NOT NULL
            AND rejection_reason IS NULL
            AND acknowledged_at IS NOT NULL)
        OR
        (status = 'REJECTED'
            AND applied_power_kw IS NOT NULL
            AND rejection_reason IS NOT NULL
            AND acknowledged_at IS NOT NULL)
        OR
        (status = 'TIMED_OUT'
            AND applied_power_kw IS NULL
            AND rejection_reason IS NOT NULL
            AND acknowledged_at IS NOT NULL)
    );
