CREATE TABLE command_deliveries (
    command_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    dispatch_id UUID NOT NULL,
    site_id UUID NOT NULL,
    device_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    requested_event_id UUID NOT NULL,
    mqtt_topic VARCHAR(500) NOT NULL,
    mqtt_payload JSONB NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    received_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(1000),
    CONSTRAINT command_deliveries_status_check CHECK (
        status IN ('PENDING', 'PUBLISHED', 'ACKNOWLEDGED')
    ),
    CONSTRAINT command_deliveries_interval_check CHECK (valid_from < expires_at),
    CONSTRAINT command_deliveries_payload_check CHECK (jsonb_typeof(mqtt_payload) = 'object'),
    CONSTRAINT command_deliveries_attempts_check CHECK (attempts >= 0),
    CONSTRAINT command_deliveries_state_check CHECK (
        (status = 'PENDING' AND published_at IS NULL AND acknowledged_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL AND acknowledged_at IS NULL)
        OR (status = 'ACKNOWLEDGED' AND acknowledged_at IS NOT NULL)
    )
);

CREATE INDEX command_deliveries_ready_idx
    ON command_deliveries (next_attempt_at, valid_from)
    WHERE status = 'PENDING';

CREATE INDEX command_deliveries_device_idx
    ON command_deliveries (organization_id, device_id, received_at DESC);
